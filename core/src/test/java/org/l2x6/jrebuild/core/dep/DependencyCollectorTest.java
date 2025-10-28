/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.dep;

import eu.maveniverse.maven.mima.context.Context;
import eu.maveniverse.maven.mima.context.Runtime;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.assertj.core.api.Assertions;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;
import org.l2x6.jrebuild.core.dep.DependencyCollectorRequest.Builder;
import org.l2x6.pom.tuner.model.Gav;
import org.l2x6.pom.tuner.model.GavtcsSet;

public class DependencyCollectorTest {
    private static final Logger log = Logger.getLogger(DependencyCollectorTest.class);

    @Test
    void includeOptionalDependencies() {
        assertDependencies(
                "",
                b -> b.includeOptionalDependencies(true),
                """
                        org.l2x6.jrebuild.test-project:jrebuild-test-api:jar:0.0.1
                        """,
                """
                        org.l2x6.jrebuild.test-project:jrebuild-test-impl:jar:0.0.1
                        +- org.l2x6.jrebuild.test-project:jrebuild-test-api:jar:0.0.1
                        `- org.l2x6.jrebuild.test-project:jrebuild-test-optional:jar:0.0.1 (optional)
                           `- org.l2x6.jrebuild.test-project:jrebuild-test-transitive:jar:0.0.1 (optional)
                        """,
                """
                        org.l2x6.jrebuild.test-project:jrebuild-test-imported:jar:0.0.1
                        `- org.l2x6.jrebuild.test-project:jrebuild-test-transitive:jar:0.0.1 (optional)
                        """);

    }

    @Test
    void excludeOptionalDependencies() {
        assertDependencies(
                "",
                c -> {
                },
                """
                        org.l2x6.jrebuild.test-project:jrebuild-test-api:jar:0.0.1
                        """,
                """
                        org.l2x6.jrebuild.test-project:jrebuild-test-impl:jar:0.0.1
                        +- org.l2x6.jrebuild.test-project:jrebuild-test-api:jar:0.0.1
                        `- org.l2x6.jrebuild.test-project:jrebuild-test-optional:jar:0.0.1 (optional)
                        """,
                """
                        org.l2x6.jrebuild.test-project:jrebuild-test-imported:jar:0.0.1
                        `- org.l2x6.jrebuild.test-project:jrebuild-test-transitive:jar:0.0.1 (optional)
                        """);

    }

    @Test
    void localSnapshots() {
        assertDependencies(
                "-SNAPSHOT",
                c -> c.projectDirectory(Path.of("target/projects/test-project")),
                """
                        org.l2x6.jrebuild.test-project:jrebuild-test-api:jar:0.0.1-SNAPSHOT
                        """,
                """
                        org.l2x6.jrebuild.test-project:jrebuild-test-impl:jar:0.0.1-SNAPSHOT
                        +- org.l2x6.jrebuild.test-project:jrebuild-test-api:jar:0.0.1-SNAPSHOT
                        `- org.l2x6.jrebuild.test-project:jrebuild-test-optional:jar:0.0.1-SNAPSHOT (optional)
                        """,
                """
                        org.l2x6.jrebuild.test-project:jrebuild-test-imported:jar:0.0.1-SNAPSHOT
                        `- org.l2x6.jrebuild.test-project:jrebuild-test-transitive:jar:0.0.1-SNAPSHOT (optional)
                        """);

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
                            ManagedGavsSelector.select(
                                    context,
                                    bom,
                                    GavtcsSet.builder().include("org.l2x6.jrebuild.test-project:*").build()));
            customizeRequestBuilder.accept(builder);
            DependencyCollectorRequest re = builder.build();
            List<String> trees = DependencyCollector.collect(context, re)
                    .sorted()
                    .map(ArtifactDependencyTree::toString)
                    .peek(log::info)
                    .collect(Collectors.toList());
            Assertions.assertThat(trees).containsExactly(expected);
        }
    }

}
