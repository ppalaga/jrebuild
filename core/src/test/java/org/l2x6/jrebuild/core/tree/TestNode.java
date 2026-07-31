/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild
 *                                 project contributors as indicated by the @author tags
 *                                 SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.tree;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;

record TestNode(String label, StringBuilder data, Collection<TestNode> children) implements Node<TestNode> {
    static TestNode empty(String label) {
        return new TestNode(label, new StringBuilder(label), new LinkedHashSet<>());
    }

    static TestNode path(String... labels) {
        TestNode result = empty(labels[0]);
        result.appendPath(tail(labels));
        return result;
    }

    @Override
    public TestNode merge(TestNode other) {
        this.data.append(",").append(other.data.toString());
        return this;
    }

    TestNode appendPath(String... nodes) {
        if (nodes.length == 0) {
            return null;
        } else {
            TestNode newNode = TestNode.empty(nodes[0]);
            TestNode result = children.stream().filter(newNode::equals).findFirst().orElse(null);
            if (result == null) {
                children.add(newNode);
                result = newNode;
            }
            if (nodes.length > 1) {
                String[] rest = tail(nodes);
                return result.appendPath(rest);
            } else {
                return result;
            }
        }
    }

    static String[] tail(String... nodes) {
        String[] rest = new String[nodes.length - 1];
        System.arraycopy(nodes, 1, rest, 0, nodes.length - 1);
        return rest;
    }

    @Override
    public int hashCode() {
        return label.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        TestNode other = (TestNode) obj;
        return Objects.equals(label, other.label);
    }

    @Override
    public String toString() {
        return label + " (" + data + ")";
    }

}
