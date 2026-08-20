/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.build;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.List;
import java.util.Objects;
import java.util.StringTokenizer;

public record ResourceMatch(String path,
        ResourceMatchLevel level,
        String diff,
        List<ResourceMatch> children) {

    public ResourceMatch(String path,
            ResourceMatchLevel level,
            String diff,
            List<ResourceMatch> children) {
        this.children = children != null ? children : List.of();
        this.diff = diff != null && diff.isEmpty() ? null : diff;
        this.level = Objects.requireNonNull(level, "level");
        this.path = Objects.requireNonNull(path, "path");
    }

    public static ResourceMatch of(ResourceMatchLevel level, String path) {
        return level.match(path);
    }

    public String toString() {
        return toString(new StringBuilder(), 0).toString();
    }

    public StringBuilder toString(StringBuilder sb, int indentLevel) {
        if (sb.length() != 0) {
            sb.append('\n');
        }
        sb.append(level);
        if (path != null || (diff != null && !diff.isEmpty())) {
            if (path != null) {
                sb.append(": ").append(path);
            }
            if (diff != null && !diff.isEmpty()) {
                if (!diff.contains("\n") && path == null) {
                    sb.append(": ").append(diff);
                } else {
                    sb.append(":");
                    StringTokenizer st = new StringTokenizer(diff, "\n\r");
                    while (st.hasMoreTokens()) {
                        sb.append('\n');
                        indent(sb, indentLevel + 1);
                        sb.append(st.nextToken());
                    }
                }
            }

        }
        if (children != null && children.size() > 0) {
            for (ResourceMatch en : children) {
                en.toString(sb, indentLevel + 1);
            }
        }
        return sb;
    }

    static StringBuilder indent(StringBuilder sb, int level) {
        for (int i = 0; i < level; i++) {
            sb.append("    ");
        }
        return sb;
    }

    public static record IndentedLine(int indentLevel, String line) {
        static final int indentSize = 4;

        @JsonCreator
        public static IndentedLine parse(String line) {
            int i = 0;
            while (line.charAt(i) == ' ') {
                i++;
                if (i >= line.length()) {
                    break;
                }
            }
            int indent = i / indentSize;
            return new IndentedLine(indent, line.substring(indent * indentSize));
        }

        public static IndentedLine of(String line) {
            return new IndentedLine(0, line);
        }

        public StringBuilder toString(StringBuilder sb) {
            return indent(sb, indentLevel).append(line);
        }

        @Override
        @JsonValue
        public String toString() {
            return toString(new StringBuilder()).toString();
        }

    }
}
