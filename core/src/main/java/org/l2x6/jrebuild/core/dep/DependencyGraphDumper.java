/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.dep;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;

import static java.util.Objects.requireNonNull;

/**
 * Adapted from {@link org.eclipse.aether.util.graph.visitor.DependencyGraphDumper}
 */
public class DependencyGraphDumper implements DependencyVisitor {

    private final Consumer<String> buffer;

    private final List<ChildInfo> childInfos = new ArrayList<>();

    public DependencyGraphDumper(Consumer<String> buffer) {
        this.buffer = requireNonNull(buffer);
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
            buffer.accept(it.next().formatIndentation(!it.hasNext()));
        }
    }

    private void formatNode(DependencyNode node) {
        Artifact a = node.getArtifact();
        Dependency d = node.getDependency();
        buffer.accept(a.toString());
        if (d != null && d.isOptional()) {
            buffer.accept(" (optional)");
        }
        buffer.accept("\n");
    }

    @Override
    public boolean visitLeave(DependencyNode node) {
        if (!childInfos.isEmpty()) {
            childInfos.remove(childInfos.size() - 1);
        }
        if (!childInfos.isEmpty()) {
            childInfos.get(childInfos.size() - 1).index++;
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
