package org.l2x6.jrebuild.core.build.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.cliassured.CliAssured;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Ref;
import org.l2x6.jrebuild.api.os.OsArch;
import org.l2x6.jrebuild.api.os.Tool;
import org.l2x6.jrebuild.api.os.Tool.InstalledTool;
import org.l2x6.jrebuild.api.scm.FqScmRef;
import org.l2x6.jrebuild.api.scm.ScmRepository;
import org.l2x6.jrebuild.common.git.GitUtils;
import org.l2x6.jrebuild.core.build.BuildGroup;
import org.l2x6.jrebuild.core.build.BuildRequest;
import org.l2x6.jrebuild.core.build.Reproducibility;
import org.l2x6.jrebuild.core.build.Resource;
import org.l2x6.jrebuild.core.build.ResourceMatchLevel;
import org.l2x6.pom.tuner.MavenRepository;
import org.l2x6.pom.tuner.model.Gav;
import org.l2x6.pom.tuner.model.Gavtc;
import org.l2x6.pom.tuner.model.Gavtcf;

/**
 * Layout:
 *
 * <pre>{@code
 * builds
 * + org/group1/project/version/<sha1>
 * | + 2026-03-04T11-22-33 // attempt timestamp
 * | | +- artifacts.txt
 * | | +- buildinfo.properties
 * | + 2026-03-05T22-33-44 // attempt timestamp
 * | | +- artifacts.txt
 * | | +- buildinfo.properties
 * }</pre>
 *
 */
public record LocalRebuildService(
        Path cloneDirectory,
        Path buildServiceRootDirectory,
        CompletableFuture<BuildMetadataLayout> lazyBuildMetadataLayout,
        LocalToolService tools,
        ResourceMatchService matchService) {

    private static final DateTimeFormatter DIR_FORMAT = null;

    static LocalRebuildService of(
            Path cloneDirectory,
            Path buildServiceRootDirectory,
            Executor executor,
            LocalToolService tools,
            ResourceMatchService matchService) {
        return new LocalRebuildService(
                cloneDirectory,
                buildServiceRootDirectory,
                CompletableFuture.supplyAsync(() -> BuildMetadataLayout.of(buildServiceRootDirectory.resolve("builds")),
                        executor),
                tools,
                matchService);
    }

    public BuildReport ensureBuilt(BuildRequest buildRequest) {
        return ensureBuilt(buildRequest, Clock.systemUTC());
    }

    public BuildReport ensureBuilt(BuildRequest buildRequest, Clock clock) {
        /* Create or find the build directory */
        Path buildDir = getLayout().findBuildDirectory(buildRequest.buildGroup());

        Reproducibility requestedRepro = buildRequest.requiredReproducibility();
        Optional<BuildReport> availableBuild;
        try (Stream<BuildReport> reports = listReports(buildDir)) {
            availableBuild = reports.filter(report -> report.reproducibility.isBetterOrSame(requestedRepro))
                    .sorted(BuildReport.byTimestamp().reversed())
                    .findFirst();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not load reports from " + buildDir, e);
        }

        if (availableBuild.isPresent()) {
            return availableBuild.get();
        }

        return build(buildDir, buildRequest, tools, matchService, clock);

    }

    static BuildReport build(Path buildGroupDir, BuildRequest buildRequest, LocalToolService tools,
            ResourceMatchService matchService, Clock clock) {
        final FqScmRef scmRef = buildRequest.scmRef();
        final ScmRepository repo = scmRef.repository();
        if (!"git".equals(repo.type())) {
            throw new IllegalStateException("Cannot checkout from SCM type " + repo.type());
        }
        OsArch currentOsArch = OsArch.current();
        if (!currentOsArch.equals(buildRequest.osArch())) {
            throw new IllegalStateException(
                    "The current OS " + currentOsArch + " does not match the requested OS " + buildRequest.osArch());
        }

        ZonedDateTime ts = ZonedDateTime.now(clock.withZone(ZoneId.of("UTC")));
        String formattedTs = ts.format(DIR_FORMAT);
        Path buildDir = buildGroupDir.resolve(formattedTs);
        Path deployDir = buildDir.resolve("deploy");
        Path cloneDir = buildDir.resolve("clone");
        try {
            Files.createDirectories(deployDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create " + deployDir, e);
        }
        try {
            Files.createDirectories(cloneDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create " + cloneDir, e);
        }

        /* Install the tools and prepare the PATH env var */
        String colon = System.getProperty("path.separator");
        StringJoiner joiner = new StringJoiner(colon);
        for (Tool tool : buildRequest.tools()) {
            InstalledTool installed = tools.install(tool);
            installed.preparePathEnvironmentVariable(joiner::add);
        }
        joiner.add(System.getenv("PATH"));
        final String pathEnvVar = joiner.toString();

        /* Checkout the sources */
        String commitId = null;
        try (Git git = GitUtils.cloneOrFetchAndReset(repo.uri(), scmRef.scmRef().name(), cloneDir, 1)) {
            final Ref ref = git.getRepository().exactRef("HEAD");
            commitId = ref.getObjectId().getName();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not fetch from " + repo.uri(), e);
        }

        /* Run the script */
        List<String> cmd = buildRequest.shell().command(buildRequest.osArch(), buildRequest.buildScript());
        CliAssured.command(cmd.get(0))
                .args(cmd.subList(1, cmd.size()))
                .cd(cloneDir)
                .env("PATH", pathEnvVar)
                .env("DEPLOYMENT_DIR", deployDir.toString())
                .stderrToStdout()
                .then()
                .stdout()
                .log()
                .execute()
                .assertSuccess();

        /* Check if everything was deployed, report missing artifacts if needed */
        Map<Gavtc, Path> builtArtifacts = collectArtifacts(deployDir);
        Map<Gavtc, ArtifactInfo> builtArtifactsMap = new LinkedHashMap<>();
        buildRequest.buildGroup().artifacts().stream()
                .filter(a -> !builtArtifacts.containsKey(a))
                .forEach(a -> builtArtifactsMap.put(a, new ArtifactInfo(ResourceMatchLevel.MISSING_IN_REBUILD)));

        Reproducibility foundReproducibility = null;

        if (!builtArtifactsMap.isEmpty()) {
            foundReproducibility = Reproducibility.UNBUILDABLE;
        }

        for (Entry<Gavtc, Path> en : builtArtifacts.entrySet()) {
            matchService.compare(null, Resource.of(deployDir.resolve(en.getValue())));
        }

        return new BuildReport(buildRequest, ts, foundReproducibility, builtArtifactsMap);
    }

    static Map<Gavtc, Path> collectArtifacts(Path deployDir) {
        MavenRepository repo = MavenRepository.local(deployDir);
        Map<Gavtc, Path> result = new TreeMap<>();
        try (Stream<Gavtcf> gavs = repo.gavtcfStream()) {
            gavs.forEach(a -> result.put(a.toGavtc(), a.getFile()));
        }
        return Collections.unmodifiableMap(result);
    }

    static Stream<BuildReport> listReports(Path buildDir) throws IOException {
        Stream<BuildReport> result;
        result = Files.list(buildDir)
                .map(f -> buildDir.resolve(f))
                .map(dir -> dir.resolve("build-report.yaml"))
                .filter(Files::isRegularFile)
                .map(buildReportYaml -> load(buildReportYaml, BuildReport.class));
        return result;
    }

    private static <T> T load(Path buildReportYaml, Class<T> cl) {
        return null;
    }

    private BuildMetadataLayout getLayout() {
        try {
            return lazyBuildMetadataLayout.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Could not get lazyBuildMetadataLayout", e);
        }
    }

    static class Workflow {

    }

    static record BuildReport(
            BuildRequest buildRequest,
            /** When the build was started */
            ZonedDateTime timeStamp,
            /**
             * Overall reproducibility aggregated over all
             */
            Reproducibility reproducibility,
            Map<Gavtc, ArtifactInfo> builtArtifacts) {

        private static Comparator<BuildReport> BY_TIMESTAMP_COMPARATOR = Comparator.comparing(BuildReport::timeStamp);

        public static Comparator<? super BuildReport> byTimestamp() {
            return BY_TIMESTAMP_COMPARATOR;
        }

    }

    static record ArtifactInfo(
            ResourceMatchLevel resourceMatchLevel) {

    }

    static record BuildMetadataLayout(
            Path buildsDirectory,
            Map<GavtcRevision, Path> buildsDirectoriesByGavtcRevision) {

        static BuildMetadataLayout of(Path buildsDirectory) {
            Map<GavtcRevision, Path> buildsDirectoriesByGavtcRevision = new ConcurrentHashMap<>();
            try (Stream<Path> files = Files.walk(buildsDirectory)) {
                files
                        .filter(f -> Files.isRegularFile(f) && f.getFileName().toString().equals("artifacts.txt"))
                        .forEach(artifactsTxt -> {
                            final Set<Gavtc> artifacts = readArtifacts(artifactsTxt);
                            Path buildDir = artifactsTxt.getParent();
                            final String revision = buildDir.getFileName().toString();
                            for (Gavtc gavtc : artifacts) {
                                final GavtcRevision gavtcRev = new GavtcRevision(gavtc, revision);
                                final Path existing = buildsDirectoriesByGavtcRevision.put(
                                        gavtcRev,
                                        buildDir);
                                if (existing != null) {
                                    throw new IllegalStateException(gavtcRev + " cannot be associated with multiple paths: "
                                            + existing + ", " + buildDir);
                                }
                            }
                        });
            } catch (IOException e) {
                throw new UncheckedIOException("Could not walk " + buildsDirectory, e);
            }
            return new BuildMetadataLayout(buildsDirectory, buildsDirectoriesByGavtcRevision);
        }

        public Path findBuildDirectory(BuildGroup buildGroup) {
            final String revision = buildGroup.scmRef().scmRef().revisionOrUnknownRevision();
            Set<Path> paths = buildGroup.artifacts().stream()
                    .map(a -> buildsDirectoriesByGavtcRevision.get(new GavtcRevision(a, revision)))
                    .collect(Collectors.toCollection(TreeSet::new));
            return switch (paths.size()) {
            case 0 -> createBuildDirectory(buildGroup);
            case 1 -> paths.iterator().next();
            default -> throw new IllegalArgumentException("Multiple build directories found for " + buildGroup + ": " + paths);
            };
        }

        Path createBuildDirectory(BuildGroup buildGroup) {
            Gav mainArtifact = buildGroup.findMainArtifact();
            FqScmRef scmRef = buildGroup.scmRef();
            Optional<String> lastPathSegment = scmRef.repository().lastPathSegment();
            if (lastPathSegment.isPresent()) {
                mainArtifact = new Gav(mainArtifact.getGroupId(), lastPathSegment.get(), mainArtifact.getVersion());
            }
            final String rev = scmRef.scmRef().revisionOrUnknownRevision();
            final Path result = buildsDirectory.resolve(mainArtifact.getRepositoryPath()).resolve(rev);
            if (!Files.exists(result)) {
                try {
                    Files.createDirectories(result);
                } catch (IOException e) {
                    throw new RuntimeException("Could not create " + result);
                }
            }
            return result;
        }

        static Set<Gavtc> readArtifacts(Path artifactsTxt) {
            try (Stream<String> lines = Files.lines(artifactsTxt, StandardCharsets.UTF_8)) {
                return Collections.unmodifiableSet(lines
                        .map(Gavtc::of)
                        .collect(Collectors.<Gavtc, Set<Gavtc>> toCollection(TreeSet::new)));
            } catch (IOException e) {
                throw new UncheckedIOException("Could not read " + artifactsTxt, e);
            }
        }

        static record GavtcRevision(Gavtc gavtc, String revision) {

            @Override
            public String toString() {
                return gavtc + "@" + revision;
            }

        }
    }

}
