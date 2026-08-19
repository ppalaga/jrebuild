/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild
 *                                 project contributors as indicated by the @author tags
 *                                 SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.api.util;

import java.util.AbstractList;
import java.util.List;
import java.util.function.Function;

public class MappedList<S, T> extends AbstractList<T> implements List<T> {
    private final List<S> delegate;
    private final Function<S, T> mapper;

    public MappedList(List<S> delegate, Function<S, T> mapper) {
        super();
        this.delegate = delegate;
        this.mapper = mapper;
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public T get(int index) {
        return mapper.apply(delegate.get(index));
    }

}
