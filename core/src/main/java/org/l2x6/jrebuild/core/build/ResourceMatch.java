package org.l2x6.jrebuild.core.build;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.List;
import java.util.Objects;

public record ResourceMatch(
        ResourceMatchLevel level,
        String path,
        List<IndentedLine> messages,
        List<ResourceMatch> children) {

    public static ResourceMatch of(ResourceMatchLevel level, String path) {
        return level.match(path);
    }

    public String toString() {
        return toString(new StringBuilder(), 0).toString();
    }

    @Override
    public int hashCode() {
        return Objects.hash(children, level, messages, path);
    }

    public StringBuilder toString(StringBuilder sb, int indentLevel) {
        if (sb.length() != 0) {
            sb.append('\n');
        }
        sb.append(level);
        if (path != null || (messages != null && !messages.isEmpty())) {
            if (path != null) {
                sb.append(": ").append(path);
            }
            if (messages != null) {
                int size = messages.size();
                if (size == 1 && path == null) {
                    sb.append(": ").append(messages.get(0));
                } else {
                    sb.append(":");
                    for (IndentedLine msg : messages) {
                        sb.append('\n');
                        indent(sb, indentLevel + 1);
                        msg.toString(sb);
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
