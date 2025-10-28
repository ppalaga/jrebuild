/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.dep;

import eu.maveniverse.maven.mima.context.Context;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;
import org.l2x6.jrebuild.core.dep.tree.DependencyTreeRequest;
import org.l2x6.pom.tuner.model.Gavtcs;
import org.l2x6.pom.tuner.model.GavtcsSet;

public class DependencyTreeRenderer {

    public DependencyTreeRenderer() {
    }

    public static void render(Context context, DependencyTreeRequest request, Consumer<String> consumer) {

        GavtcsSet rootBomGavSet = GavtcsSet.builder()
                .includePatterns(request.rootBomIncludes())
                .excludePatterns(request.excludes())
                .build();
        Set<Gavtcs> rootGavtcs = new LinkedHashSet<>(/*ManagedGavsSelector.select(context, request.rootBom(), rootBomGavSet)*/);
        rootGavtcs.addAll(request.rootArtifacts());
        DependencyCollectorRequest re = new DependencyCollectorRequest(
                request.projectDirectory(),
                request.rootBom(),
                rootGavtcs,
                request.additionalBoms(),
                false);
        DependencyCollector.collect(context, re)
                .sorted()
                .forEach(tree -> {
                    tree.rootNode().accept(new DependencyGraphDumper(consumer));
                });

    }
}
