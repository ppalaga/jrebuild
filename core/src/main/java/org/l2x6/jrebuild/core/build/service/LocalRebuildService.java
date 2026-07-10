package org.l2x6.jrebuild.core.build.service;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.vertx.mutiny.core.Vertx;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.concurrent.Executor;
import java.util.stream.Stream;
import org.cliassured.CliAssured;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Ref;
import org.l2x6.jrebuild.api.os.OsArch;
import org.l2x6.jrebuild.api.os.Tool;
import org.l2x6.jrebuild.api.os.Tool.InstalledTool;
import org.l2x6.jrebuild.api.scm.FqScmRef;
import org.l2x6.jrebuild.api.scm.ScmRepository;
import org.l2x6.jrebuild.common.CommonUtils;
import org.l2x6.jrebuild.common.git.GitUtils;
import org.l2x6.jrebuild.core.build.BuildReport;
import org.l2x6.jrebuild.core.build.BuildRequest;
import org.l2x6.jrebuild.core.build.Reproducibility;
import org.l2x6.jrebuild.core.build.Resource;
import org.l2x6.jrebuild.core.build.ResourceMatch;
import org.l2x6.jrebuild.core.build.ResourceMatchLevel;
import org.l2x6.jrebuild.core.maven.LocalMavenRepository;
import org.l2x6.jrebuild.core.maven.ReferenceMavenRepository;
import org.l2x6.pom.tuner.model.Gavtc;
import org.l2x6.pom.tuner.model.Gavtcf;
import org.l2x6.pom.tuner.model.OptionalWithDefault;

public record LocalRebuildService(
        Vertx vertx,
        Path cloneDirectory,
        Path buildServiceRootDirectory,
        BuildMetadataLayout buildMetadataLayout,
        LocalToolService tools,
        ReferenceMavenRepository referenceMavenRepository,
        ResourceMatchService matchService,
        BuildReportStorage buildReportStorage) {

    static LocalRebuildService of(
            Vertx vertx,
            Path cloneDirectory,
            Path buildServiceRootDirectory,
            Executor executor,
            LocalToolService tools,
            ReferenceMavenRepository referenceMavenRepository,
            ResourceMatchService matchService,
            BuildReportStorage buildReportStorage) {

        return new LocalRebuildService(
                vertx,
                cloneDirectory,
                buildServiceRootDirectory,
                new BuildMetadataLayout(buildServiceRootDirectory.resolve("builds")),
                tools,
                referenceMavenRepository,
                matchService,
                buildReportStorage);

    }

    public BuildReport ensureBuilt(BuildRequest buildRequest, Reproducibility requiredReproducibility) {
        return ensureBuilt(buildRequest, requiredReproducibility, Clock.systemUTC());
    }

    public BuildReport ensureBuilt(BuildRequest buildRequest, Reproducibility requiredReproducibility, Clock clock) {
        /* Create or find the build directory */
        Path buildDir = buildMetadataLayout.getOrCreateBuildDirectory(buildRequest.buildGroup());

        Optional<BuildReport> availableBuild;
        try (Stream<BuildReport> reports = buildReportStorage.list(buildRequest.buildGroup())) {
            availableBuild = reports.filter(report -> report.reproducibility().isBetterOrSame(requiredReproducibility))
                    .sorted(BuildReport.byBestReproducibilityAndNewestTimestamp())
                    .findFirst();
        }

        if (availableBuild.isPresent()) {
            return availableBuild.get();
        }

        BuildReport buildReport = build(vertx, buildDir, buildRequest, tools, matchService, referenceMavenRepository, clock);
        buildReportStorage.store(buildReport);
        return buildReport;

    }

    static BuildReport build(Vertx vertx, Path buildGroupDir, BuildRequest buildRequest, LocalToolService tools,
            ResourceMatchService matchService, ReferenceMavenRepository referenceMavenRepository, Clock clock) {
        final FqScmRef scmRef = buildRequest.buildGroup().scmRef();
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
        String formattedTs = BuildMetadataLayout.format(ts);
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
            return new BuildReport(
                    buildRequest,
                    ts,
                    Duration.between(ts, ZonedDateTime.now(clock.withZone(ZoneId.of("UTC")))),
                    Reproducibility.INVALID_SOURCE_INFO,
                    Map.of(),
                    commitId,
                    "Could not fetch from " + repo.uri() + "\n" + CommonUtils.stackTrace(e));
        }

        /* Run the script */
        List<String> cmd = buildRequest.shell().command(buildRequest.osArch(), buildRequest.buildScript());
        try {
            CliAssured.command(cmd.get(0))
                    .args(cmd.subList(1, cmd.size()))
                    .cd(cloneDir)
                    .env("PATH", pathEnvVar)
                    .env("DEPLOYMENT_REPO", deployDir.toUri().toString())
                    .stderrToStdout()
                    .then()
                    .stdout()
                    .log()
                    .execute()
                    .assertSuccess();
        } catch (Throwable e) {
            return new BuildReport(
                    buildRequest,
                    ts,
                    Duration.between(ts, ZonedDateTime.now(clock.withZone(ZoneId.of("UTC")))),
                    Reproducibility.UNBUILDABLE,
                    Map.of(),
                    commitId,
                    "Could not build " + repo.uri() + "\n" + CommonUtils.stackTrace(e));
        }

        /* Check if everything was deployed, report missing artifacts if needed */
        Multi<Gavtcf> rebuiltArtifacts = new LocalMavenRepository(deployDir, vertx.fileSystem()).gavtcfStream();
        Map<Gavtc, ResourceMatch> builtArtifactsMap = new TreeMap<>(
                Gavtc.groupFirstComparator(OptionalWithDefault.valueOrDefaultComparator()));

        rebuiltArtifacts.onItem().transformToUniAndMerge(rebuiltGavtcf -> referenceMavenRepository
                .resolve(rebuiltGavtcf.toGavtc())
                .chain(refGavtcsf -> Uni.createFrom().item(() -> {
                    ResourceMatch match = matchService.compare(Resource.of(refGavtcsf.getFile()),
                            Resource.of(rebuiltGavtcf.getFile()));
                    return new ArtifactInfo(rebuiltGavtcf.toGavtc(), match);
                })
                        .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())))
                .collect().asList().await().indefinitely()
                .forEach(ai -> builtArtifactsMap.put(ai.gavtc, ai.resourceMatch));

        buildRequest.buildGroup().artifacts().stream()
                .filter(a -> !builtArtifactsMap.containsKey(a))
                .forEach(a -> builtArtifactsMap.put(
                        a,
                        ResourceMatch.of(ResourceMatchLevel.MISSING_IN_REBUILD, a.getRepositoryPath())));

        Reproducibility reproducibility = builtArtifactsMap.values().stream()
                .map(ResourceMatch::level)
                .sorted(Comparator.comparing(ResourceMatchLevel::ordinal))
                .findFirst()
                .orElseThrow()
                .reproducibility();

        return new BuildReport(
                buildRequest,
                ts,
                Duration.between(ts, ZonedDateTime.now(clock.withZone(ZoneId.of("UTC")))),
                reproducibility,
                builtArtifactsMap,
                commitId,
                null);
    }

    public static record ArtifactInfo(
            Gavtc gavtc,
            ResourceMatch resourceMatch) {
    }

}
