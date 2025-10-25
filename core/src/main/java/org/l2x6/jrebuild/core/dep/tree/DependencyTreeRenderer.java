/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.dep.tree;

import eu.maveniverse.maven.mima.context.Context;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;
import org.l2x6.jrebuild.core.dep.DependencyCollector;
import org.l2x6.jrebuild.core.dep.DependencyCollectorRequest;
import org.l2x6.jrebuild.core.dep.ManagedGavsSelector;
import org.l2x6.pom.tuner.model.Gavtcs;
import org.l2x6.pom.tuner.model.GavtcsSet;

import static java.util.Objects.requireNonNull;

public class DependencyTreeRenderer {

    public DependencyTreeRenderer() {
    }

    public static void render(Context context, DependencyTreeRequest request, Consumer<String> consumer) {

        GavtcsSet rootBomGavSet = GavtcsSet.builder()
                .includePatterns(request.rootBomIncludes())
                .excludePatterns(request.excludes())
                .build();
        Set<Gavtcs> rootGavtcs = new LinkedHashSet<>(ManagedGavsSelector.select(context, request.rootBom(), rootBomGavSet));
        rootGavtcs.addAll(request.rootArtifacts());
        DependencyCollectorRequest re = new DependencyCollectorRequest(
                request.projectDirectory(),
                request.rootBom(),
                rootGavtcs,
                request.additionalBoms());
        DependencyCollector.collect(context, re)
                .sorted()
                .forEach(tree -> {
                    tree.rootNode().accept(new DependencyGraphDumper(consumer));
                });

    }

    /**
     * Adapted from {@link org.eclipse.aether.util.graph.visitor.DependencyGraphDumper}
     */
    static class DependencyGraphDumper implements DependencyVisitor {

        private final Consumer<String> consumer;
        private final StringBuilder buffer = new StringBuilder();

        private final List<ChildInfo> childInfos = new ArrayList<>();

        public DependencyGraphDumper(Consumer<String> consumer) {
            this.consumer = requireNonNull(consumer);
        }

        @Override
        public boolean visitEnter(DependencyNode node) {
            formatIndentation();
            formatNode(node);
            childInfos.add(new ChildInfo(node.getChildren().size()));
            return true;
        }

        private void formatIndentation() {
            for (Iterator<ChildInfo> it = childInfos.iterator(); it.hasNext();) {
                buffer.append(it.next().formatIndentation(!it.hasNext()));
            }
        }

        private void formatNode(DependencyNode node) {
            Artifact a = node.getArtifact();
            Dependency d = node.getDependency();
            buffer.append(a);
            if (d != null && d.isOptional()) {
                buffer.append(" (optional)");
            }
            buffer.append('\n');
        }

        @Override
        public boolean visitLeave(DependencyNode node) {
            if (!childInfos.isEmpty()) {
                childInfos.remove(childInfos.size() - 1);
            }
            if (!childInfos.isEmpty()) {
                childInfos.get(childInfos.size() - 1).index++;
            }
            if (childInfos.isEmpty()) {
                consumer.accept(buffer.toString());
            }
            return true;
        }

        private static class ChildInfo {

            final int count;

            int index;

            ChildInfo(int count) {
                this.count = count;
            }

            public String formatIndentation(boolean end) {
                boolean last = index + 1 >= count;
                if (end) {
                    return last ? "`- " : "+- ";
                }
                return last ? "   " : "|  ";
            }
        }
    }
}
