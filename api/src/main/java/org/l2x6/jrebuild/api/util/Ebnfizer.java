/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild
 *                                 project contributors as indicated by the @author tags
 *                                 SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.api.util;

import java.util.stream.Stream;

/**
 * A utility for generalizing multiple similar strings into a single
 * <a href="https://en.wikipedia.org/wiki/Extended_Backus%E2%80%93Naur_form">EBNF</a>-like representation.
 *
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public class Ebnfizer {

    private final CharNode root;

    public Ebnfizer() {
        super();
        this.root = new CharNode(CharNode.ZERO);
    }

    public StringBuilder append(StringBuilder sb) {
        root.append(sb);
        return sb;
    }

    @Override
    public String toString() {
        return append(new StringBuilder()).toString();
    }

    public void add(String string) {
        root.add(string);
    }

    public Ebnfizer add(Stream<String> strings) {
        strings.forEach(root::add);
        return this;
    }

    static class CharNode {
        private static final char ZERO = (char) 0;
        private final char ch;
        private CharNode[] children;

        public CharNode(char ch) {
            super();
            this.ch = ch;
        }

        public void append(StringBuilder sb) {
            if (children == null || children.length == 0) {
                return;
            } else if (children.length == 1) {
                CharNode child = children[0];
                if (child.ch != ZERO) {
                    sb.append(child.ch);
                }
                child.append(sb);
            } else {
                boolean isTerminal = children[0].ch == ZERO;
                if (isTerminal) {
                    /* the suffix is optional */
                    sb.append('[');
                    for (int i = 1; i < children.length; i++) {
                        final CharNode child = children[i];
                        if (i > 1) {
                            sb.append('|');
                        }
                        if (child.ch != ZERO) {
                            sb.append(child.ch);
                        }
                        child.append(sb);
                    }
                    sb.append(']');
                } else {
                    /* the suffix is non-optional */
                    sb.append('(');
                    for (int i = 0; i < children.length; i++) {
                        final CharNode child = children[i];
                        if (i > 0) {
                            sb.append('|');
                        }
                        sb.append(child.ch);
                        child.append(sb);
                    }
                    sb.append(')');
                }
            }
        }

        public void add(String chars) {
            add(chars.toCharArray(), 0);
        }

        public void add(char[] chars, int offset) {
            char c = chars[offset++];
            CharNode childNode = getOrCreateChild(c);
            if (offset < chars.length) {
                childNode.add(chars, offset);
            } else if (offset == chars.length) {
                childNode.getOrCreateChild(ZERO);
            }
        }

        CharNode getOrCreateChild(char c) {
            if (children == null || children.length == 0) {
                final CharNode result = new CharNode(c);
                this.children = new CharNode[] { result };
                return result;
            } else {
                int i = 0;
                for (; i < children.length; i++) {
                    final CharNode child = children[i];
                    if (child.ch == c) {
                        return child;
                    }
                    if (child.ch > c) {
                        break;
                    }
                }
                CharNode[] newChildren = new CharNode[children.length + 1];
                if (i > 0) {
                    System.arraycopy(children, 0, newChildren, 0, i);
                }
                CharNode result = newChildren[i] = new CharNode(c);
                if (i < children.length) {
                    System.arraycopy(children, i, newChildren, i + 1, children.length - i);
                }
                children = newChildren;
                return result;
            }

        }
    }

}
