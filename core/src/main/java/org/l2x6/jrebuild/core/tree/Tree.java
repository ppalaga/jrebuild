/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild
 *                                 project contributors as indicated by the @author tags
 *                                 SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.tree;

import java.util.ArrayList;
import java.util.List;

public record Tree<T extends Node<T>>(List<T> rootNodes) {

    public static class Builder<T extends Node<T>> {
        private final List<T> children = new ArrayList<>();

        public Tree<T> build() {
            return new Tree<>(children);
        }

    }

}
