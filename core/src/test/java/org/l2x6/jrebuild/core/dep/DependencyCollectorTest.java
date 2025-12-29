/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.dep;

import eu.maveniverse.maven.mima.context.Context;
import eu.maveniverse.maven.mima.context.ContextOverrides;
import eu.maveniverse.maven.mima.context.Runtime;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.assertj.core.api.Assertions;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;
import org.l2x6.jrebuild.core.dep.DependencyCollectorRequest.Builder;
import org.l2x6.jrebuild.core.mima.internal.CachingMavenModelReader;
import org.l2x6.jrebuild.core.tree.PrintVisitor;
import org.l2x6.pom.tuner.model.Gav;
import org.l2x6.pom.tuner.model.Gavtc;
import org.l2x6.pom.tuner.model.GavtcsPattern;
import org.l2x6.pom.tuner.model.GavtcsSet;

public class DependencyCollectorTest {
    private static final Logger log = Logger.getLogger(DependencyCollectorTest.class);

    @Test
    void includeOptionalDependencies() {
        assertDependencies(
                "",
                b -> b
                        .includeOptionalDependencies(true)
                        .includeParentsAndImports(false),
                """
                        org.l2x6.jrebuild.test-project:jrebuild-test-api:0.0.1:jar
                        """,
                """
                        org.l2x6.jrebuild.test-project:jrebuild-test-impl:0.0.1:jar
                        +- org.l2x6.jrebuild.test-project:jrebuild-test-api:0.0.1:jar
                        `- org.l2x6.jrebuild.test-project:jrebuild-test-optional:0.0.1:jar
                           `- org.l2x6.jrebuild.test-transitive:jrebuild-test-transitive:0.0.1:jar
                        """,
                """
                        org.l2x6.jrebuild.test-project:jrebuild-test-imported:0.0.1:jar
                        `- org.l2x6.jrebuild.test-transitive:jrebuild-test-transitive:0.0.1:jar
                        """);

    }

    @Test
    void excludeOptionalDependencies() {
        assertDependencies(
                "",
                b -> b.includeParentsAndImports(false),
                """
                        org.l2x6.jrebuild.test-project:jrebuild-test-api:0.0.1:jar
                        """,
                """
                        org.l2x6.jrebuild.test-project:jrebuild-test-impl:0.0.1:jar
                        +- org.l2x6.jrebuild.test-project:jrebuild-test-api:0.0.1:jar
                        `- org.l2x6.jrebuild.test-project:jrebuild-test-optional:0.0.1:jar
                        """,
                """
                        org.l2x6.jrebuild.test-project:jrebuild-test-imported:0.0.1:jar
                        `- org.l2x6.jrebuild.test-transitive:jrebuild-test-transitive:0.0.1:jar
                        """);

    }

    @Test
    void localSnapshots() {
        assertDependencies(
                "-SNAPSHOT",
                b -> b
                        .projectDirectory(Path.of("target/projects/test-project"))
                        .includeParentsAndImports(false),
                """
                        org.l2x6.jrebuild.test-project:jrebuild-test-api:0.0.1-SNAPSHOT:jar
                        """,
                """
                        org.l2x6.jrebuild.test-project:jrebuild-test-impl:0.0.1-SNAPSHOT:jar
                        +- org.l2x6.jrebuild.test-project:jrebuild-test-api:0.0.1-SNAPSHOT:jar
                        `- org.l2x6.jrebuild.test-project:jrebuild-test-optional:0.0.1-SNAPSHOT:jar
                        """,
                """
                        org.l2x6.jrebuild.test-project:jrebuild-test-imported:0.0.1-SNAPSHOT:jar
                        `- org.l2x6.jrebuild.test-transitive:jrebuild-test-transitive:0.0.1-SNAPSHOT:jar
                        """);

    }

    @Test
    void includeParentsAndImports() {
        Runtime runtime = org.l2x6.jrebuild.core.mima.JRebuildRuntime.getInstance();
        eu.maveniverse.maven.mima.context.ContextOverrides.Builder overrides = JrebuildTestUtils.testRepo();
        try (Context context = runtime.create(overrides.build())) {
            DependencyCollectorRequest re = DependencyCollectorRequest.builder()
                    .rootArtifacts(Gavtc.of("org.l2x6.jrebuild.test-project:jrebuild-test-build-child:0.0.1:jar"))
                    .build();
            List<String> trees = DependencyCollector.collect(context, re)
                    .collect().asList().await().indefinitely().stream().sorted()
                    .map(PrintVisitor::toString)
                    .peek(tree -> log.infof("Dependencies:\n%s", tree))
                    .collect(Collectors.toList());
            String[] expected = {
                    """
                            org.l2x6.jrebuild.test-project:jrebuild-test-build-child:0.0.1:jar
                            +- org.l2x6.jrebuild.test-project:jrebuild-test-build-parent:0.0.1:pom
                            |  +- org.l2x6.jrebuild.test-project:jrebuild-test-project:0.0.1:pom
                            |  `- org.l2x6.jrebuild.test-project:jrebuild-test-bom:0.0.1:pom
                            |     +- org.l2x6.jrebuild.test-project:jrebuild-test-project:0.0.1:pom
                            |     `- org.l2x6.jrebuild.test-project:jrebuild-test-imported-bom:0.0.1:pom
                            |        `- org.l2x6.jrebuild.test-project:jrebuild-test-project:0.0.1:pom
                            `- org.l2x6.jrebuild.test-project:jrebuild-test-api:0.0.1:jar
                               `- org.l2x6.jrebuild.test-project:jrebuild-test-project:0.0.1:pom
                            """
            };
            Assertions.assertThat(trees).containsExactly(expected);
        }

    }

    @Test
    void additionalBoms() {
        /* intermediary dependends on api 1.0.0
         * but the external-bom:2.0.0 overrides it to 2.0.0 */

        Runtime runtime = org.l2x6.jrebuild.core.mima.JRebuildRuntime.getInstance();
        eu.maveniverse.maven.mima.context.ContextOverrides.Builder overrides = JrebuildTestUtils.testRepo();
        try (Context context = runtime.create(overrides.build())) {
            DependencyCollectorRequest re = DependencyCollectorRequest.builder()
                    .rootBom(Gav.of("org.l2x6.jrebuild.test-project:jrebuild-test-bom:0.0.1"))
                    .rootArtifacts(Gavtc.of("org.l2x6.jrebuild.external:jrebuild-external-impl:2.0.0"))
                    .additionalBoms(Gav.of("org.l2x6.jrebuild.external:jrebuild-external-bom:2.0.0"))
                    .includeParentsAndImports(false)
                    .build();
            List<String> trees = DependencyCollector.collect(context, re)
                    .collect().asList().await().indefinitely().stream().sorted()
                    .map(PrintVisitor::toString)
                    .peek(tree -> log.infof("Dependencies:\n%s", tree))
                    .collect(Collectors.toList());
            String[] expected = {
                    """
                            org.l2x6.jrebuild.external:jrebuild-external-impl:2.0.0
                            `- org.l2x6.jrebuild.external:jrebuild-external-intermediary:2.0.0:jar
                               `- org.l2x6.jrebuild.external:jrebuild-external-api:2.0.0:jar
                            """
            };
            Assertions.assertThat(trees).containsExactly(expected);
        }

    }

    @Test
    void excludes() {
        /* If we exclude a non-existent dependency DependencyCollector must not fail */

        final Path mavenRepoDeployment = Path.of("target/repo");
        final String relPath = "org/l2x6/jrebuild/external/jrebuild-external-non-existent";
        final String suffix = "-" + UUID.randomUUID().toString();

        Runtime runtime = org.l2x6.jrebuild.core.mima.JRebuildRuntime.getInstance();
        ContextOverrides overrides = JrebuildTestUtils.testRepo().build();
        final Path remotePath = mavenRepoDeployment.resolve(relPath);
        final Path localPath = overrides.getLocalRepositoryOverride().resolve(relPath);
        Assertions.assertThat(remotePath).exists();
        final List<Path> hidePaths = List.of(remotePath, localPath);

        final DependencyCollectorRequest re = DependencyCollectorRequest.builder()
                .rootArtifacts(Gavtc.of("org.l2x6.jrebuild.external:jrebuild-external-non-existent-dependency-owner:2.0.0"))
                .excludes(GavtcsPattern.of("org.l2x6.jrebuild.external:jrebuild-external-non-existent"))
                .includeParentsAndImports(false)
                .build();

        /* First try with jrebuild-external-non-existent being resolvable in the remote repo */
        try (Context context = runtime.create(overrides)) {

            List<String> trees = DependencyCollector.collect(context, re)
                    .collect().asList().await().indefinitely().stream().sorted()
                    .map(PrintVisitor::toString)
                    .peek(tree -> log.infof("Dependencies:\n%s", tree))
                    .collect(Collectors.toList());
            String[] expected = {
                    """
                            org.l2x6.jrebuild.external:jrebuild-external-non-existent-dependency-owner:2.0.0
                            """
            };
            Assertions.assertThat(trees).containsExactly(expected);
        }
        /* Make sure that the resolved did not attempt to download jrebuild-external-non-existent */
        Assertions.assertThat(localPath).doesNotExist();

        /* Second, remove the jrebuild-external-non-existent from remote maven repo
         * and the DependencyCollector should still not fail */
        hideBadDep(remotePath, suffix);
        try (Context context = runtime.create(overrides)) {

            List<String> trees = DependencyCollector.collect(context, re)
                    .collect().asList().await().indefinitely().stream().sorted()
                    .map(PrintVisitor::toString)
                    .peek(tree -> log.infof("Dependencies:\n%s", tree))
                    .collect(Collectors.toList());
            String[] expected = {
                    """
                            org.l2x6.jrebuild.external:jrebuild-external-non-existent-dependency-owner:2.0.0
                            """
            };
            Assertions.assertThat(trees).containsExactly(expected);
            /* Make sure that the resolved did not attempt to download jrebuild-external-non-existent */
            Assertions.assertThat(localPath).doesNotExist();
        } finally {
            revealBadDep(remotePath, suffix);
        }

    }

    static void hideBadDep(Path badDepPath, String suffix) {
        Path dest = badDepPath.getParent().resolve(badDepPath.getFileName().toString() + suffix);
        try {
            Files.move(badDepPath, dest);
        } catch (IOException e) {
            throw new RuntimeException("Could not move " + badDepPath + " -> " + dest, e);
        }
    }

    static void revealBadDep(Path badDepPath, String suffix) {
        if (Files.exists(badDepPath)) {
            Path dest = badDepPath.getParent().resolve(badDepPath.getFileName().toString() + "-deleted-" + UUID.randomUUID());
            try {
                Files.move(badDepPath, dest);
            } catch (IOException e) {
                throw new RuntimeException("Could not move " + dest + " -> " + badDepPath, e);
            }
        }
        Path dest = badDepPath.getParent().resolve(badDepPath.getFileName().toString() + suffix);
        try {
            Files.move(dest, badDepPath);
        } catch (IOException e) {
            throw new RuntimeException("Could not move " + dest + " -> " + badDepPath, e);
        }
    }

    private void assertDependencies(String snapshot, Consumer<Builder> customizeRequestBuilder, String... expected) {
        Runtime runtime = org.l2x6.jrebuild.core.mima.JRebuildRuntime.getInstance();
        eu.maveniverse.maven.mima.context.ContextOverrides.Builder overrides = JrebuildTestUtils.testRepo();
        if (!snapshot.isEmpty()) {
            overrides.withBasedirOverride(Path.of("target/projects/test-project"));
        }
        try (Context context = runtime.create(overrides.build())) {
            final Gav bom = Gav.of("org.l2x6.jrebuild.test-project:jrebuild-test-bom:0.0.1" + snapshot);

            Builder builder = DependencyCollectorRequest.builder()
                    .rootArtifacts(
                            new ManagedGavsSelector(
                                    context.lookup().lookup(CachingMavenModelReader.class).get()::readEffectiveModel).select(
                                            bom,
                                            GavtcsSet.builder().include("org.l2x6.jrebuild.test-project:*").build()));
            customizeRequestBuilder.accept(builder);
            DependencyCollectorRequest re = builder.build();
            List<String> trees = DependencyCollector.collect(context, re)
                    .collect().asList().await().indefinitely().stream().sorted()
                    .map(PrintVisitor::toString)
                    .peek(tree -> log.infof("Dependencies:\n%s", tree))
                    .collect(Collectors.toList());
            Assertions.assertThat(trees).containsExactly(expected);
        }
    }

}
