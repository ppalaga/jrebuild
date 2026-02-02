/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.common;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.l2x6.jrebuild.api.scm.JrebuildUtils;

public class JrebuildCommonUtilsTest {

    @Test
    void assertImmutableList() {
        final String ELEM = "foo";
        Stream.of(
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
                Collections.unmodifiableList(Arrays.asList(ELEM)))
                .forEach(list -> {
                    JrebuildUtils.assertImmutable(list);
                });
        Assertions.assertThatThrownBy(() -> JrebuildUtils.assertImmutable(new ArrayList<String>()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Mutable list: " + ArrayList.class.getName());

    }

    @Test
    void assertImmutableSet() {
        int i = 0;
        Stream.of(
                Set.of(),
                Set.of(String.valueOf(i++)),
                Set.of(String.valueOf(i++), String.valueOf(i++)),
                Set.of(String.valueOf(i++), String.valueOf(i++), String.valueOf(i++)),
                Set.of(String.valueOf(i++), String.valueOf(i++), String.valueOf(i++), String.valueOf(i++)),
                Set.of(String.valueOf(i++), String.valueOf(i++), String.valueOf(i++), String.valueOf(i++), String.valueOf(i++)),
                Set.of(String.valueOf(i++), String.valueOf(i++), String.valueOf(i++), String.valueOf(i++), String.valueOf(i++),
                        String.valueOf(i++)),
                Set.of(String.valueOf(i++), String.valueOf(i++), String.valueOf(i++), String.valueOf(i++), String.valueOf(i++),
                        String.valueOf(i++), String.valueOf(i++)),
                Set.of(String.valueOf(i++), String.valueOf(i++), String.valueOf(i++), String.valueOf(i++), String.valueOf(i++),
                        String.valueOf(i++), String.valueOf(i++), String.valueOf(i++)),
                Set.of(String.valueOf(i++), String.valueOf(i++), String.valueOf(i++), String.valueOf(i++), String.valueOf(i++),
                        String.valueOf(i++), String.valueOf(i++), String.valueOf(i++), String.valueOf(i++)),
                Set.of(String.valueOf(i++), String.valueOf(i++), String.valueOf(i++), String.valueOf(i++), String.valueOf(i++),
                        String.valueOf(i++), String.valueOf(i++), String.valueOf(i++), String.valueOf(i++),
                        String.valueOf(i++)),
                Set.of(String.valueOf(i++), String.valueOf(i++), String.valueOf(i++), String.valueOf(i++), String.valueOf(i++),
                        String.valueOf(i++), String.valueOf(i++), String.valueOf(i++), String.valueOf(i++), String.valueOf(i++),
                        String.valueOf(i++)),
                Collections.emptySet(),
                Collections.singleton(String.valueOf(i++)),
                Collections.unmodifiableSet(new HashSet<>(Arrays.asList(String.valueOf(i++)))))
                .forEach(list -> {
                    JrebuildUtils.assertImmutable(list);
                });
        Assertions.assertThatThrownBy(() -> JrebuildUtils.assertImmutable(new TreeSet<String>()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Mutable set: " + TreeSet.class.getName());
        Assertions.assertThatThrownBy(() -> JrebuildUtils.assertImmutable(new HashSet<String>()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Mutable set: " + HashSet.class.getName());
        Assertions.assertThatThrownBy(() -> JrebuildUtils.assertImmutable(new LinkedHashSet<String>()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Mutable set: " + LinkedHashSet.class.getName());

    }
}
