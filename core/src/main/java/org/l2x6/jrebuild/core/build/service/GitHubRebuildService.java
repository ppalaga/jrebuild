package org.l2x6.jrebuild.core.build.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.l2x6.jrebuild.api.scm.FqScmRef;
import org.l2x6.jrebuild.core.build.Arch;
import org.l2x6.jrebuild.core.build.BuildGroup;
import org.l2x6.jrebuild.core.build.BuildRequest;
import org.l2x6.jrebuild.core.build.Os;
import org.l2x6.jrebuild.core.build.Reproducibility;
import org.l2x6.jrebuild.core.build.Shell;
import org.l2x6.pom.tuner.model.Gav;
import org.l2x6.pom.tuner.model.Gavtc;

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
public record GitHubRebuildService(
        Path buildServiceRootDirectory,
        CompletableFuture<BuildMetadataLayout> lazyBuildMetadataLayout) {

    static GitHubRebuildService of(Path buildServiceRootDirectory, Executor executor) {
        return new GitHubRebuildService(
                buildServiceRootDirectory,
                CompletableFuture.supplyAsync(() -> BuildMetadataLayout.of(buildServiceRootDirectory.resolve("builds")),
                        executor));
    }

    public void ensureBuilt(BuildRequest buildRequest) {
        /* Clone the build repo */

        /* Create or find the build directory */
        Path buildDir = getLayout().findBuildDirectory(buildRequest.buildGroup());

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
            Reproducibility reproducibility,
            Map<Gavtc, ArtifactInfo> artifacts,
            Os os,
            Arch arch,
            String shell,
            String cd,
            String buildScript,
            List<Tool> tools

    ) {

    }

    static record Tool(
            /** The command name, such as {@code mvn}, {@code java} without directory. Should be installed in PATH */
            String executable,
            String version,
            /** Used esp. for Java; e.g. {@code temurin} or {@code corretto} */
            String distribution,
            Packager packager) {

    }

    public static interface Packager {
        String name();

        void installSelf(Os os, Arch arch, Shell shell, Consumer<Entry<String, String>> env);

        void install(Tool tool, Consumer<Entry<String, String>> env);
    }

    public static enum WellKnownPackager implements Packager {
        sdkman() {

            @Override
            public void installSelf(Os os, Arch arch, Shell shell, Consumer<Entry<String, String>> env) {
                if (shell != Shell.BASH) {
                    throw new IllegalArgumentException(
                            "Cannot install SDKMAN on " + shell + " shell. Only " + Shell.BASH + " is supported");
                }

            }

            @Override
            public void install(Tool tool, Consumer<Entry<String, String>> env) {

            }

        };
    }

    static record ArtifactInfo(
            Reproducibility reproducibility) {

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
