/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.mima.internal;

import eu.maveniverse.maven.mima.context.Context;
import eu.maveniverse.maven.mima.context.Lookup;
import eu.maveniverse.maven.mima.extensions.mmr.MavenModelReader;
import java.util.Optional;
import java.util.function.Supplier;

public class JRebuildLookup implements Lookup {
    private final Supplier<Context> lazyContext;
    private volatile Optional<CachingMavenModelReader> modelReader;
    private final Object lock = new Object();

    public JRebuildLookup(Supplier<Context> lazyContext) {
        this.lazyContext = lazyContext;
    }

    @Override
    public <T> Optional<T> lookup(Class<T> type) {
        if (MavenModelReader.class.isAssignableFrom(type)) {
            Optional<?> mr;
            if ((mr = modelReader) == null) {
                synchronized (lock) {
                    if ((mr = modelReader) == null) {
                        mr = modelReader = Optional.of(new CachingMavenModelReader(lazyContext.get()));
                    }
                }
            }
            return (Optional<T>) mr;
        }
        return Optional.empty();
    }

    @Override
    public <T> Optional<T> lookup(Class<T> type, String name) {
        return lookup(type);
    }

}
