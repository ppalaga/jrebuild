/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.dep.tree;

import eu.maveniverse.maven.mima.context.Context;
import eu.maveniverse.maven.mima.context.ContextOverrides;
import eu.maveniverse.maven.mima.context.Runtime;
import eu.maveniverse.maven.mima.context.Runtimes;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.l2x6.pom.tuner.model.Gav;
import org.l2x6.pom.tuner.model.GavtcsPattern;

public class DependencyTreeRendererTest {

    @Test
    void render() {
        Runtime runtime = Runtimes.INSTANCE.getRuntime();
        try (Context context = runtime.create(ContextOverrides.create().build())) {
            Gav bom = Gav.of("io.quarkiverse.cxf:quarkus-cxf-bom:3.29.0");
            DependencyTreeRequest request = new DependencyTreeRequest(
                    null,
                    bom,
                    Stream.of("io.quarkiverse.cxf:*").map(GavtcsPattern::of).collect(Collectors.toList()),
                    List.of(),
                    List.of(),
                    Stream.of("io.quarkus:quarkus-bom:3.29.0").map(Gav::of).collect(Collectors.toList()),
                    List.of(),
                    List.of(),
                    null);
            StringBuilder sb = new StringBuilder();
            DependencyTreeRenderer.render(context, request, s -> sb.append(s).append('\n'));
            System.out.println(sb.toString());
        }

    }

}
