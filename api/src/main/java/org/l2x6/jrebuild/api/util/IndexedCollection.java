/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild
 *                                 project contributors as indicated by the @author tags
 *                                 SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.api.util;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.function.Function;

public class IndexedCollection<K, E> implements Collection<E> {

    private final Map<K, E> map;
    private final Function<E, K> getKey;
    private final BiFunction<E, E, E> merge;

    public static <K, E> IndexedCollection<K, E> ordered(Function<E, K> getKey, BiFunction<E, E, E> merge) {
        return new IndexedCollection<>(new TreeMap<>(), getKey, merge);
    }

    public static <K, E> IndexedCollection<K, E> linked(Function<E, K> getKey, BiFunction<E, E, E> merge) {
        return new IndexedCollection<>(new LinkedHashMap<>(), getKey, merge);
    }

    public IndexedCollection(Map<K, E> map, Function<E, K> getKey, BiFunction<E, E, E> merge) {
        super();
        this.map = map;
        this.getKey = getKey;
        this.merge = merge;
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return map.values().contains(o);
    }

    @Override
    public Iterator<E> iterator() {
        return map.values().iterator();
    }

    @Override
    public Object[] toArray() {
        return map.values().toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return map.values().toArray(a);
    }

    @Override
    public boolean add(E e) {
        K k = getKey.apply(e);
        E old = map.get(k);
        if (old == null) {
            map.put(k, e);
            return true;
        }
        map.put(k, merge.apply(old, e));
        return false;
    }

    @Override
    public boolean remove(Object o) {
        K k = getKey.apply((E) o);
        return map.remove(k) != null;
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return map.values().containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        boolean result = false;
        for (E e : c) {
            result |= add(e);
        }
        return result;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        boolean result = false;
        for (Object e : c) {
            result |= remove(e);
        }
        return result;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        map.clear();
    }

    @Override
    public int hashCode() {
        return map.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        IndexedCollection other = (IndexedCollection) obj;
        return map.equals(other.map);
    }

    public E computeIfAbsent(K k, Function<K, E> create) {
        return map.computeIfAbsent(k, create);
    }

}
