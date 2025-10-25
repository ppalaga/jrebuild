/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.dep;

import eu.maveniverse.maven.mima.context.Context;
import eu.maveniverse.maven.mima.context.ContextOverrides;
import eu.maveniverse.maven.mima.context.ContextOverrides.AddRepositoriesOp;
import eu.maveniverse.maven.mima.context.Runtime;
import eu.maveniverse.maven.mima.context.Runtimes;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.junit.jupiter.api.Test;
import org.l2x6.pom.tuner.model.Gav;
import org.l2x6.pom.tuner.model.Gavtcs;
import org.l2x6.pom.tuner.model.GavtcsSet;

public class ManagedGavsSelectorTest {

    @Test
    void select() {
        Runtime runtime = Runtimes.INSTANCE.getRuntime();
        RemoteRepository localRepo = new RemoteRepository.Builder(
                "local-test",
                "default",
                Path.of(".").toAbsolutePath().normalize().resolve("target/repo").toUri().toString())
                .setSnapshotPolicy(new RepositoryPolicy(true, "never", "ignore"))
                .build();
        ContextOverrides overrides = ContextOverrides.create()
                .addRepositoriesOp(AddRepositoriesOp.REPLACE)
                .repositories(List.of(localRepo))
                .build();
        try (Context context = runtime.create(overrides)) {
            final Gav bom = Gav.of("org.l2x6.jrebuild.test-project:jrebuild-test-project:0.0.1-SNAPSHOT");
            assertGavSet(
                    context,
                    bom,
                    GavtcsSet.builder().include("org.l2x6.jrebuild.test-project:*").build(),
                    "org.apache.cxf:cxf-rt-security-saml:4.1.3:jar", "org.apache.cxf:cxf-rt-security:4.1.3:jar");
        }
    }

    static void assertGavSet(Context context, Gav bom, GavtcsSet gavtcsSet, String... expected) {
        Set<Gavtcs> result = ManagedGavsSelector.select(context, bom, gavtcsSet);
        Assertions.assertThat(result).isEqualTo(Stream.of(expected).map(Gavtcs::of).collect(Collectors.toSet()));
    }
}
