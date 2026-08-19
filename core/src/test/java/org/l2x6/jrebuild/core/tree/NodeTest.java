/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild
 *                                 project contributors as indicated by the @author tags
 *                                 SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.tree;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class NodeTest {

    @Test
    void mergeSame() {
        TestNode forest = TestNode.empty("root");
        TestNode t1 = TestNode.path("1.1", "2.1");

        forest.adopt(t1);
        Assertions.assertThat(PrintVisitor.<TestNode> stringBuilderPrintVisitor().walk(forest).toString())
                .isEqualTo("""
                        root (root)
                        `- 1.1 (1.1)
                           `- 2.1 (2.1)
                        """);
        forest.adopt(t1);
        Assertions.assertThat(PrintVisitor.<TestNode> stringBuilderPrintVisitor().walk(forest).toString())
                .isEqualTo("""
                        root (root)
                        `- 1.1 (1.1)
                           `- 2.1 (2.1)
                        """);

        TestNode t1_ = TestNode.path("1.1", "2.1");
        forest.adopt(t1_);
        Assertions.assertThat(PrintVisitor.<TestNode> stringBuilderPrintVisitor().walk(forest).toString())
                .isEqualTo("""
                        root (root)
                        `- 1.1 (1.1,1.1)
                           `- 2.1 (2.1,2.1)
                        """);

    }

    @Test
    void mergeSibling() {
        TestNode forest = TestNode.empty("root");
        TestNode t1 = TestNode.path("1.1", "2.1");

        forest.adopt(t1);
        Assertions.assertThat(PrintVisitor.<TestNode> stringBuilderPrintVisitor().walk(forest).toString())
                .isEqualTo("""
                        root (root)
                        `- 1.1 (1.1)
                           `- 2.1 (2.1)
                        """);
        TestNode t2 = TestNode.path("1.2", "2.2");
        forest.adopt(t2);
        Assertions.assertThat(PrintVisitor.<TestNode> stringBuilderPrintVisitor().walk(forest).toString())
                .isEqualTo("""
                        root (root)
                        +- 1.1 (1.1)
                        |  `- 2.1 (2.1)
                        `- 1.2 (1.2)
                           `- 2.2 (2.2)
                        """);

        TestNode t2_ = TestNode.path("1.2", "2.2");
        forest.adopt(t2_);
        Assertions.assertThat(PrintVisitor.<TestNode> stringBuilderPrintVisitor().walk(forest).toString())
                .isEqualTo("""
                        root (root)
                        +- 1.1 (1.1)
                        |  `- 2.1 (2.1)
                        `- 1.2 (1.2,1.2)
                           `- 2.2 (2.2,2.2)
                        """);

    }

    @Test
    void mergeReplace() {
        TestNode forest = TestNode.empty("root");
        TestNode t1 = TestNode.path("1.1", "2.1");

        forest.adopt(t1);
        Assertions.assertThat(PrintVisitor.<TestNode> stringBuilderPrintVisitor().walk(forest).toString())
                .isEqualTo("""
                        root (root)
                        `- 1.1 (1.1)
                           `- 2.1 (2.1)
                        """);

        TestNode t2 = TestNode.path("0.1", "1.1");
        forest.adopt(t2);
        Assertions.assertThat(PrintVisitor.<TestNode> stringBuilderPrintVisitor().walk(forest).toString())
                .isEqualTo("""
                        root (root)
                        `- 0.1 (0.1)
                           `- 1.1 (1.1,1.1)
                              `- 2.1 (2.1)
                        """);

        TestNode t2_ = TestNode.path("0.1", "1.1");
        forest.adopt(t2_);
        Assertions.assertThat(PrintVisitor.<TestNode> stringBuilderPrintVisitor().walk(forest).toString())
                .isEqualTo("""
                        root (root)
                        `- 0.1 (0.1,0.1)
                           `- 1.1 (1.1,1.1,1.1)
                              `- 2.1 (2.1)
                        """);

    }

    @Test
    void mergeReplaceEnd() {
        TestNode forest = TestNode.empty("root");
        TestNode t1 = TestNode.path("1.1", "2.1");
        forest.adopt(t1);
        forest.appendPath("1.2", "2.2");
        Assertions.assertThat(PrintVisitor.<TestNode> stringBuilderPrintVisitor().walk(forest).toString())
                .isEqualTo("""
                        root (root)
                        +- 1.1 (1.1)
                        |  `- 2.1 (2.1)
                        `- 1.2 (1.2)
                           `- 2.2 (2.2)
                        """);

        TestNode t2 = TestNode.path("0.1", "1.2", "2.3");
        t2.appendPath("1.3");
        forest.adopt(t2);
        Assertions.assertThat(PrintVisitor.<TestNode> stringBuilderPrintVisitor().walk(forest).toString())
                .isEqualTo("""
                        root (root)
                        +- 1.1 (1.1)
                        |  `- 2.1 (2.1)
                        `- 0.1 (0.1)
                           +- 1.2 (1.2,1.2)
                           |  +- 2.3 (2.3)
                           |  `- 2.2 (2.2)
                           `- 1.3 (1.3)
                        """);

    }

    @Test
    void mergeKeepOrder() {
        TestNode forest = TestNode.empty("root");
        forest.appendPath("1.1");
        forest.appendPath("1.2");
        forest.appendPath("1.3");

        TestNode t2 = TestNode.path("0.1", "1.1");
        forest.adopt(t2);
        Assertions.assertThat(PrintVisitor.<TestNode> stringBuilderPrintVisitor().walk(forest).toString())
                .isEqualTo("""
                        root (root)
                        +- 0.1 (0.1)
                        |  `- 1.1 (1.1,1.1)
                        +- 1.2 (1.2)
                        `- 1.3 (1.3)
                        """);
    }

    @Test
    void mergeGrandChild() {
        TestNode forest = TestNode.empty("root");
        TestNode t1 = TestNode.path("1.1", "2.1");

        forest.adopt(t1);
        Assertions.assertThat(PrintVisitor.<TestNode> stringBuilderPrintVisitor().walk(forest).toString())
                .isEqualTo("""
                        root (root)
                        `- 1.1 (1.1)
                           `- 2.1 (2.1)
                        """);

        TestNode t2 = TestNode.path("1.1", "2.2");
        forest.adopt(t2);
        Assertions.assertThat(PrintVisitor.<TestNode> stringBuilderPrintVisitor().walk(forest).toString())
                .isEqualTo("""
                        root (root)
                        `- 1.1 (1.1,1.1)
                           +- 2.1 (2.1)
                           `- 2.2 (2.2)
                        """);

        TestNode t2_ = TestNode.path("1.1", "2.2");
        forest.adopt(t2_);
        Assertions.assertThat(PrintVisitor.<TestNode> stringBuilderPrintVisitor().walk(forest).toString())
                .isEqualTo("""
                        root (root)
                        `- 1.1 (1.1,1.1,1.1)
                           +- 2.1 (2.1)
                           `- 2.2 (2.2,2.2)
                        """);

    }

    @Test
    void mergeGrandChildren() {
        TestNode forest = TestNode.empty("root");
        TestNode t1 = TestNode.path("1.1", "2.1");

        forest.adopt(t1);
        Assertions.assertThat(PrintVisitor.<TestNode> stringBuilderPrintVisitor().walk(forest).toString())
                .isEqualTo("""
                        root (root)
                        `- 1.1 (1.1)
                           `- 2.1 (2.1)
                        """);

        TestNode t2 = TestNode.path("1.1", "2.2");
        t2.appendPath("2.3");
        forest.adopt(t2);
        Assertions.assertThat(PrintVisitor.<TestNode> stringBuilderPrintVisitor().walk(forest).toString())
                .isEqualTo("""
                        root (root)
                        `- 1.1 (1.1,1.1)
                           +- 2.1 (2.1)
                           +- 2.2 (2.2)
                           `- 2.3 (2.3)
                        """);

    }

}
