/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild
 *                                 project contributors as indicated by the @author tags
 *                                 SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.tree;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

public interface Node<T extends Node<T>> {
    Collection<T> children();

    default T find(T node) {
        return new Internal.FindVisitor<T>(node).walk((T) this).result();
    }

    /**
     * Merge any mutable data from {@code other} {@link Node} to this {@link Node}.
     * This method must not change child nodes.
     * The default implementation does nothing.
     *
     * @param  other a {@link Node} whose data should be merged into this {@link Node}
     * @return       this {@link Node}
     */
    default T merge(T other) {
        return (T) this;
    }

    default T adopt(T newChild) {
        T found = this.find(newChild);
        if (found != null) {
            if (found == newChild) {
                /* Noting to do */
                return (T) this;
            }
            found.merge(newChild);
            for (T ch : newChild.children()) {
                found.adopt(ch);
            }
            return (T) this;
        }
        /* Try the other way round */
        for (T ch : children()) {
            T newChildDescendant = newChild.find(ch);
            if (newChildDescendant != null) {
                if (newChildDescendant == ch) {
                    /* Noting to do */
                    return (T) this;
                }
                newChildDescendant.adopt(ch);
                replaceChild(ch, newChild);
                return (T) this;
            }
        }
        this.children().add(newChild);
        return (T) this;
    }

    default void replaceChild(T oldChild, T newChild) {
        Collection<T> children = children();
        if (children instanceof List<T> list) {
            /* Keep ordering if possible */
            Internal.replace(list, oldChild, newChild);
        } else if (children instanceof LinkedHashSet<T> lhs) {
            /* Keep ordering also for LinkedHashSet */
            List<T> list = new ArrayList<>(lhs);
            Internal.replace(list, oldChild, newChild);
            lhs.clear();
            lhs.addAll(list);
        } else {
            children.remove(oldChild);
            children.add(newChild);
        }
    }

    default <V extends Visitor<T, V>> boolean accept(Visitor<T, V> visitor) {
        try {
            if (visitor.enter((T) this)) {
                for (T child : children()) {
                    if (!child.accept(visitor)) {
                        break;
                    }
                }
            }
            return visitor.leave((T) this);
        } catch (RuntimeException e) {
            throw new RuntimeException("Exception while visiting " + this, e);
        }
    }

    static class Internal {
        static <T> void replace(List<T> list, T oldChild, T newChild) {
            int i = list.indexOf(oldChild);
            if (i >= 0) {
                list.set(i, newChild);
            } else {
                list.add(newChild);
            }
        }

        static class FindVisitor<T extends Node<T>> implements Visitor<T, FindVisitor<T>> {
            private final T query;
            private T result;

            public FindVisitor(T query) {
                super();
                this.query = query;
            }

            public T result() {
                return result;
            }

            @Override
            public boolean enter(T node) {
                if (query.equals(node)) {
                    result = node;
                }
                return result == null;
            }

            @Override
            public boolean leave(T node) {
                return result == null;
            }

        }
    }
}
