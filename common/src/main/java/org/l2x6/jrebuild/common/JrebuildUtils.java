package org.l2x6.jrebuild.common;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JrebuildUtils {
    private JrebuildUtils() {
    }

    private static final Object ELEM = new Object();
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
            Collections.unmodifiableList(Arrays.asList(ELEM))
            ).map(Object::getClass).collect(Collectors.toSet());

    public static boolean isImmutableListType(Class<?> type) {
        return IMMUTABLE_LIST_TYPES.contains(type);
    }
}
