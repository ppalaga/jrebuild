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
import org.l2x6.jrebuild.core.mima.internal.CachingMavenModelReader;
import org.l2x6.jrebuild.core.tree.PrintVisitor;
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
                        org.l2x6.jrebuild.test-project:jrebuild-test-api:0.0.1:jar
                        """,
                """
                        org.l2x6.jrebuild.test-project:jrebuild-test-impl:0.0.1:jar
                        +- org.l2x6.jrebuild.test-project:jrebuild-test-api:0.0.1:jar
                        `- org.l2x6.jrebuild.test-project:jrebuild-test-optional:0.0.1:jar
                           `- org.l2x6.jrebuild.test-project:jrebuild-test-transitive:0.0.1:jar
                        """,
                """
                        org.l2x6.jrebuild.test-project:jrebuild-test-imported:0.0.1:jar
                        `- org.l2x6.jrebuild.test-project:jrebuild-test-transitive:0.0.1:jar
                        """);

    }

    @Test
    void excludeOptionalDependencies() {
        assertDependencies(
                "",
                c -> {
                },
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
                        `- org.l2x6.jrebuild.test-project:jrebuild-test-transitive:0.0.1:jar
                        """);

    }

    @Test
    void localSnapshots() {
        assertDependencies(
                "-SNAPSHOT",
                c -> c.projectDirectory(Path.of("target/projects/test-project")),
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
                        `- org.l2x6.jrebuild.test-project:jrebuild-test-transitive:0.0.1-SNAPSHOT:jar
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
                            new ManagedGavsSelector(
                                    context.lookup().lookup(CachingMavenModelReader.class).get()::readEffectiveModel).select(
                                            bom,
                                            GavtcsSet.builder().include("org.l2x6.jrebuild.test-project:*").build()));
            customizeRequestBuilder.accept(builder);
            DependencyCollectorRequest re = builder.build();
            List<String> trees = DependencyCollector.collect(context, re)
                    .sorted()
                    .map(PrintVisitor::toString)
                    .peek(tree -> log.infof("Dependencies:\n%s", tree))
                    .collect(Collectors.toList());
            Assertions.assertThat(trees).containsExactly(expected);
        }
    }

}
