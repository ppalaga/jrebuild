/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild
 *                                 project contributors as indicated by the @author tags
 *                                 SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.tree;

import io.smallrye.mutiny.Multi;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Let <i>Stem</i> of a <i>Tree</i> (specified by the root {@link Node}) be the maximal rooted subtree (set of adjacent
 * nodes reachable from the
 * root {@link Node}) whose nodes satisfy some {@link Predicate}.
 * Then, the operation of cutting the Stem of a Tree can be defined as
 * collecting the Subtrees (specified by their root {@link Node}s) directly reachable from the Stem but not belonging to
 * the Stem.
 * <p>
 * Example: If the input tree is
 *
 * <pre>
 * 1
 * +-3
 * | `-5
 * |   `-2
 * |     `-1
 * `-7
 *   +-4
 *   `-6
 * </pre>
 *
 * Then the Stem defined by {@link Predicate} "is odd" is
 *
 * <pre>
 * 1
 * +-3
 * | `-5
 * `-7
 * </pre>
 *
 * and its complement (i.e. the result of Cut stem operation) is the set of three Trees
 *
 * <pre>
 * 2
 * `-1
 * </pre>
 *
 * <pre>
 * 4
 * </pre>
 *
 * and
 *
 * <pre>
 * 6
 * </pre>
 *
 * @param <T> the type of the node
 * @param <V> the type of this {@link Visitor}
 */
public class CutStemVisitor<T extends Node<T>, V extends CutStemVisitor<T, V>> implements Visitor<T, V> {

    private final Set<T> stemComplement = new LinkedHashSet<>();
    private final Predicate<T> stemSelector;

    public CutStemVisitor(Predicate<T> stemSelector) {
        super();
        this.stemSelector = stemSelector;
    }

    public Multi<T> result() {
        return Multi.createFrom().iterable(stemComplement);
    }

    @Override
    public boolean enter(T node) {
        if (stemSelector.test(node)) {
            return true;
        } else {
            stemComplement.add(node);
            return false;
        }
    }

    @Override
    public boolean leave(T node) {
        return true;
    }

}
