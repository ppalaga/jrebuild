/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild
 *                                 project contributors as indicated by the @author tags
 *                                 SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.tree;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestNodeTest {

    @Test
    void testNode() {
        TestNode forest = TestNode.empty("root");
        forest.appendPath("1.1", "2.1", "3.1");
        forest.appendPath("1.1", "2.2", "3.1");

        Assertions.assertThat(PrintVisitor.<TestNode> stringBuilderPrintVisitor().walk(forest).toString())
                .isEqualTo("""
                        root (root)
                        `- 1.1 (1.1)
                           +- 2.1 (2.1)
                           |  `- 3.1 (3.1)
                           `- 2.2 (2.2)
                              `- 3.1 (3.1)
                        """);

        forest.appendPath("1.2", "2.2", "3.1");
        Assertions.assertThat(PrintVisitor.<TestNode> stringBuilderPrintVisitor().walk(forest).toString())
                .isEqualTo("""
                        root (root)
                        +- 1.1 (1.1)
                        |  +- 2.1 (2.1)
                        |  |  `- 3.1 (3.1)
                        |  `- 2.2 (2.2)
                        |     `- 3.1 (3.1)
                        `- 1.2 (1.2)
                           `- 2.2 (2.2)
                              `- 3.1 (3.1)
                        """);

        forest.appendPath("1.2", "2.2", "3.1");
        Assertions.assertThat(PrintVisitor.<TestNode> stringBuilderPrintVisitor().walk(forest).toString())
                .isEqualTo("""
                        root (root)
                        +- 1.1 (1.1)
                        |  +- 2.1 (2.1)
                        |  |  `- 3.1 (3.1)
                        |  `- 2.2 (2.2)
                        |     `- 3.1 (3.1)
                        `- 1.2 (1.2)
                           `- 2.2 (2.2)
                              `- 3.1 (3.1)
                        """);

    }
}
