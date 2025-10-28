/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.dep;

import eu.maveniverse.maven.mima.context.Context;
import eu.maveniverse.maven.mima.context.Runtime;
import eu.maveniverse.maven.mima.context.Runtimes;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.l2x6.jrebuild.core.source.tree.SourceTreeArtifactLocator;
import org.l2x6.pom.tuner.model.Gav;
import org.l2x6.pom.tuner.model.Gavtcs;
import org.l2x6.pom.tuner.model.GavtcsSet;

public class ManagedGavsSelectorTest {

    @Test
    void selectRemote() {
        Runtime runtime = Runtimes.INSTANCE.getRuntime();
        try (Context context = runtime.create(JrebuildTestUtils.testRepo().build())) {
            final Gav bom = Gav.of("org.l2x6.jrebuild.test-project:jrebuild-test-bom:0.0.1");
            assertGavSet(
                    context,
                    bom,
                    GavtcsSet.builder().include("org.l2x6.jrebuild.test-project:*").build(),
                    "org.l2x6.jrebuild.test-project:jrebuild-test-api:0.0.1:jar",
                    "org.l2x6.jrebuild.test-project:jrebuild-test-impl:0.0.1:jar",
                    "org.l2x6.jrebuild.test-project:jrebuild-test-imported:0.0.1:jar");
        }
    }

    @Test
    void selectLocal() {
        Runtime runtime = Runtimes.INSTANCE.getRuntime();
        try (Context context = runtime.create(JrebuildTestUtils.testRepo()
                .withBasedirOverride(Path.of("target/projects/test-project"))
                .build())) {
            final Gav bom = Gav.of("org.l2x6.jrebuild.test-project:jrebuild-test-bom:0.0.1");
            assertGavSet(
                    context,
                    bom,
                    GavtcsSet.builder().include("org.l2x6.jrebuild.test-project:*").build(),
                    "org.l2x6.jrebuild.test-project:jrebuild-test-api:0.0.1:jar",
                    "org.l2x6.jrebuild.test-project:jrebuild-test-impl:0.0.1:jar",
                    "org.l2x6.jrebuild.test-project:jrebuild-test-imported:0.0.1:jar");
        }
    }

    static void assertGavSet(Context context, Gav bom, GavtcsSet gavtcsSet, String... expected) {
        final SourceTreeArtifactLocator locator = new SourceTreeArtifactLocator(null, s -> true);
        final ManagedGavsSelector managedGavsSelector = new ManagedGavsSelector(locator);
        Set<Gavtcs> result = managedGavsSelector.select(context, bom, gavtcsSet);
        Assertions.assertThat(result).isEqualTo(Stream.of(expected).map(Gavtcs::of).collect(Collectors.toSet()));
    }
}
