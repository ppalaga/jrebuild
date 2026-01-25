/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.tree;

import java.util.Iterator;
import java.util.List;

public interface Node<T extends Node<T>> {
    List<T> children();
    boolean isMutable();

    default <V extends Visitor<T, V>> boolean accept(Visitor<T, V> visitor) {
        if (visitor.enter((T) this)) {
            for (T child : children()) {
                if (!child.accept(visitor)) {
                    break;
                }
            }
        }
        return visitor.leave((T) this);
    }

    default boolean deepEquals(T other) {
        if (!this.equals(other)) {
            return false;
        }
        List<T> ch1 = this.children();
        List<T> ch2 = other.children();
        if (ch1.size() != ch2.size()) {
            return false;
        }
        Iterator<T> it1 = ch1.iterator();
        Iterator<T> it2 = ch2.iterator();
        while (it1.hasNext()) {
            if (!it1.next().deepEquals(it2.next())) {
                return false;
            }
        }
        return true;
    }
}
