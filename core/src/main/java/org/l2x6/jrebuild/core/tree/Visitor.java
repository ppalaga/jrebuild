/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.tree;

/**
 * Adapted from {@link org.eclipse.aether.graph.DependencyVisitor}.
 *
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public interface Visitor<T extends Node<T>, V extends Visitor<T, V>> {

    /**
     * Notifies the visitor of a node visit before its children have been processed.
     *
     * @param  node The dependency node being visited, must not be {@code null}.
     * @return      {@code true} to visit child nodes of the specified node as well, {@code false} to skip children.
     */
    boolean enter(T node);

    /**
     * Notifies the visitor of a node visit after its children have been processed. Note that this method is always
     * invoked regardless whether any children have actually been visited.
     *
     * @param  node The dependency node being visited, must not be {@code null}.
     * @return      {@code true} to visit siblings nodes of the specified node as well, {@code false} to skip siblings.
     */
    boolean leave(T node);

    default V walk(T node) {
        node.accept(this);
        return (V) this;
    }
}
