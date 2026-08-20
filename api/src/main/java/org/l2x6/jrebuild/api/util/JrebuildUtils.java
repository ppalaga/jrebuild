/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.api.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeSet;
import java.util.Vector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JrebuildUtils {
    private JrebuildUtils() {
    }

    private static final Object ELEM = new Object();
    private static final Set<Class<?>> MUTABLE_LIST_TYPES = Set.of(ArrayList.class, LinkedList.class, Vector.class,
            Arrays.asList().getClass());

    private static final Set<Class<?>> IMMUTABLE_LIST_TYPES = Stream.of(
            List.of(),
            List.of(ELEM),
            List.of(ELEM, ELEM),
            List.of(ELEM, ELEM, ELEM),
            List.of(ELEM, ELEM, ELEM, ELEM),
            List.of(ELEM, ELEM, ELEM, ELEM, ELEM),
            List.of(ELEM, ELEM, ELEM, ELEM, ELEM, ELEM),
            List.of(ELEM, ELEM, ELEM, ELEM, ELEM, ELEM, ELEM),
            List.of(ELEM, ELEM, ELEM, ELEM, ELEM, ELEM, ELEM, ELEM),
            List.of(ELEM, ELEM, ELEM, ELEM, ELEM, ELEM, ELEM, ELEM, ELEM),
            List.of(ELEM, ELEM, ELEM, ELEM, ELEM, ELEM, ELEM, ELEM, ELEM, ELEM),
            List.of(ELEM, ELEM, ELEM, ELEM, ELEM, ELEM, ELEM, ELEM, ELEM, ELEM, ELEM),
            Collections.emptyList(),
            Collections.singletonList(ELEM),
            Collections.unmodifiableList(Arrays.asList(ELEM))).map(Object::getClass).collect(Collectors.toSet());

    private static final Set<Class<?>> MUTABLE_SET_TYPES = Set.of(HashSet.class, LinkedHashSet.class, TreeSet.class);

    private static final Set<Class<?>> IMMUTABLE_SET_TYPES = Stream.of(
            Set.of(),
            Set.of(new Object()),
            Set.of(new Object(), new Object()),
            Set.of(new Object(), new Object(), new Object()),
            Set.of(new Object(), new Object(), new Object(), new Object()),
            Set.of(new Object(), new Object(), new Object(), new Object(), new Object()),
            Set.of(new Object(), new Object(), new Object(), new Object(), new Object(), new Object()),
            Set.of(new Object(), new Object(), new Object(), new Object(), new Object(), new Object(), new Object()),
            Set.of(new Object(), new Object(), new Object(), new Object(), new Object(), new Object(), new Object(),
                    new Object()),
            Set.of(new Object(), new Object(), new Object(), new Object(), new Object(), new Object(), new Object(),
                    new Object(), new Object()),
            Set.of(new Object(), new Object(), new Object(), new Object(), new Object(), new Object(), new Object(),
                    new Object(), new Object(), new Object()),
            Set.of(new Object(), new Object(), new Object(), new Object(), new Object(), new Object(), new Object(),
                    new Object(), new Object(), new Object(), new Object()),
            Collections.emptySet(),
            Collections.singleton(new Object()),
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList(new Object())))).map(Object::getClass)
            .collect(Collectors.toSet());

    public static <T> List<T> assertImmutable(List<T> list) {
        Class<? extends List> type = list.getClass();
        if (IMMUTABLE_LIST_TYPES.contains(type)) {
            return list;
        }
        if (MUTABLE_LIST_TYPES.contains(type)) {
            throw new IllegalStateException("Mutable list: " + type.getName());
        }
        try {
            list.add(null);
            throw new IllegalStateException("Mutable list: " + type.getName());
        } catch (UnsupportedOperationException expected) {
            return list;
        }
    }

    public static <T> Set<T> assertImmutable(Set<T> list) {
        Class<? extends Set> type = list.getClass();
        if (IMMUTABLE_SET_TYPES.contains(type)) {
            return list;
        }
        if (MUTABLE_SET_TYPES.contains(type)) {
            throw new IllegalStateException("Mutable set: " + type.getName());
        }
        try {
            list.add(null);
            throw new IllegalStateException("Mutable set: " + type.getName());
        } catch (UnsupportedOperationException expected) {
            return list;
        }
    }

    public static Throwable rootCause(Throwable e) {
        while (e.getCause() != null) {
            e = e.getCause();
        }
        return e;
    }

    public static Stream<String> lines(String string) {
        return Stream.iterate(
                new StringTokenizer(string, "\r\n"),
                StringTokenizer::hasMoreTokens,
                t -> t).map(StringTokenizer::nextToken);
    }

    public static boolean contains(byte[] array, byte value) {
        for (byte b : array) {
            if (b == value)
                return true;
        }
        return false;
    }
}
