/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.tree;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/**
 * Adapted from {@link org.eclipse.aether.util.graph.visitor.DependencyGraphDumper}
 */
public class PrintVisitor<T extends Node<T>> implements Visitor<T, PrintVisitor<T>> {

    private final Consumer<String> buffer;
    private final Function<T, String> nodeFormatter;

    private final List<ChildInfo> childInfos = new ArrayList<>();

    public static <T extends Node<T>> String toString(T node) {
        StringBuilderPrintVisitor<T> v = stringBuilderPrintVisitor();
        return v.walk(node).toString();
    }

    public static <T extends Node<T>> StringBuilderPrintVisitor<T> stringBuilderPrintVisitor() {
        return new StringBuilderPrintVisitor<>(new StringBuilder());
    }

    public static <T extends Node<T>> StringBuilderPrintVisitor<T> stringBuilderPrintVisitor(
            Function<T, String> nodeFormatter) {
        return new StringBuilderPrintVisitor<>(nodeFormatter, new StringBuilder());
    }

    public PrintVisitor(Consumer<String> buffer) {
        this(n -> String.valueOf(n), buffer);
    }

    public PrintVisitor(Function<T, String> nodeFormatter, Consumer<String> buffer) {
        this.nodeFormatter = nodeFormatter;
        this.buffer = requireNonNull(buffer);
    }

    @Override
    public boolean enter(T node) {
        formatIndentation();
        buffer.accept(nodeFormatter.apply(node));
        buffer.accept("\n");
        childInfos.add(new ChildInfo(node.children().size()));
        return true;
    }

    private void formatIndentation() {
        for (Iterator<ChildInfo> it = childInfos.iterator(); it.hasNext();) {
            buffer.accept(it.next().formatIndentation(!it.hasNext()));
        }
    }

    @Override
    public boolean leave(T node) {
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

    public static class StringBuilderPrintVisitor<T extends Node<T>> extends PrintVisitor<T> {

        private final StringBuilder sb;

        public StringBuilderPrintVisitor(StringBuilder sb) {
            super(sb::append);
            this.sb = sb;
        }

        public StringBuilderPrintVisitor(Function<T, String> nodeFormatter, StringBuilder sb) {
            super(nodeFormatter, sb::append);
            this.sb = sb;
        }

        @Override
        public String toString() {
            return sb.toString();
        }

    }

}
