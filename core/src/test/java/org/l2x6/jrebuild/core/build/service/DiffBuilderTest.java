/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.build.service;

import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.l2x6.jrebuild.core.build.ResourceMatch.IndentedLine;
import org.l2x6.jrebuild.core.build.ResourceMatchLevel;
import org.l2x6.jrebuild.core.build.service.ResourceMatchService.BaseResourceMatchService.ClassFileMatchService.DiffBuilder;

public class DiffBuilderTest {

    @Test
    void conditionalSection() {
        DiffBuilder b = new DiffBuilder();

        b.add(ResourceMatchLevel.PERFECT, "Hello");

        Assertions.assertThat(b.level).isEqualTo(ResourceMatchLevel.PERFECT);
        Assertions.assertThat(b.messages).isEqualTo(List.of(new IndentedLine(0, "Hello")));

        b.conditionalSection("s0", add -> {
        });
        Assertions.assertThat(b.level).isEqualTo(ResourceMatchLevel.PERFECT);
        Assertions.assertThat(b.messages).isEqualTo(List.of(new IndentedLine(0, "Hello")));

        b.conditionalSection("s1", add -> add.add(ResourceMatchLevel.BUILDABLE, "item"));
        Assertions.assertThat(b.level).isEqualTo(ResourceMatchLevel.BUILDABLE);
        Assertions.assertThat(b.messages).isEqualTo(
                List.of(
                        new IndentedLine(0, "Hello"),
                        new IndentedLine(0, "s1"),
                        new IndentedLine(1, "item")));

        b.conditionalSection("s2", add -> {
            add.add(ResourceMatchLevel.BUILDABLE, "item1");
            add.add(ResourceMatchLevel.BUILDABLE, "item2");
        });
        Assertions.assertThat(b.level).isEqualTo(ResourceMatchLevel.BUILDABLE);
        Assertions.assertThat(b.messages).isEqualTo(
                List.of(
                        new IndentedLine(0, "Hello"),
                        new IndentedLine(0, "s1"),
                        new IndentedLine(1, "item"),
                        new IndentedLine(0, "s2"),
                        new IndentedLine(1, "item1"),
                        new IndentedLine(1, "item2")));

    }

    @Test
    void indent() {
        DiffBuilder b = new DiffBuilder();
        b.add(ResourceMatchLevel.PERFECT, "Hello");
        b.indented(add -> {
            b.conditionalSection("s2", add2 -> {
                add2.add(ResourceMatchLevel.BUILDABLE, "item1");
                add2.add(ResourceMatchLevel.BUILDABLE, "item2");
            });
        });
        Assertions.assertThat(b.level).isEqualTo(ResourceMatchLevel.BUILDABLE);
        Assertions.assertThat(b.messages).isEqualTo(
                List.of(
                        new IndentedLine(0, "Hello"),
                        new IndentedLine(1, "s2"),
                        new IndentedLine(2, "item1"),
                        new IndentedLine(2, "item2")));
    }
}
