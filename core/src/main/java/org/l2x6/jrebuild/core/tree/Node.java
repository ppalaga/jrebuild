/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.tree;

import java.util.List;

public interface Node<T extends Node<T>> {
    List<T> children();

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
}
