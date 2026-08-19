/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild
 *                                 project contributors as indicated by the @author tags
 *                                 SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.api.util;

import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class EbnfizerTest {

    @Test
    void charNode() {
        assertCharNode("(ba(r|z)|foo)", "foo", "bar", "baz");
        assertCharNode("foo", "foo");
        assertCharNode("foo-(1|2)", "foo-1", "foo-2");
        assertCharNode("foo-(1234|2456)", "foo-1234", "foo-2456");
        assertCharNode("foo", "foo", "foo", "foo");
        assertCharNode("foo[bar]", "foo", "foobar");
        assertCharNode("foo[bar|maz]", "foo", "foobar", "foomaz");
        assertCharNode("foo[bar]", "foobar", "foo");
        assertCharNode("fo[o[bar]]", "foobar", "foo", "fo");
        assertCharNode("fo[o[ba(r|z)]]", "foobar", "foo", "fo", "foobaz");
    }

    static void assertCharNode(String expected, String... strings) {
        Ebnfizer root = new Ebnfizer();

        Stream.of(strings).forEach(root::add);

        StringBuilder sb = new StringBuilder();
        root.append(sb);
        Assertions.assertThat(sb.toString()).isEqualTo(expected);

    }
}
