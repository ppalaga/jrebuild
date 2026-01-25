package org.l2x6.jrebuild.common;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

public class JrebuildUtilsTest {

    @Test

    void isImmutableListType() {
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
                Collections.unmodifiableList(Arrays.asList(ELEM))
                )
        .forEach(list -> {

            Assertions.assertThat(JrebuildUtils.isImmutableListType(list.getClass())).isTrue();

        });
        Assertions.assertThat(JrebuildUtils.isImmutableListType(ArrayList.class)).isFalse();

    }
}
