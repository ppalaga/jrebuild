/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild
 *                                 project contributors as indicated by the @author tags
 *                                 SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.tree;

import java.util.List;
import java.util.function.Predicate;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class CutStemVisitorTest {
    static final Predicate<TestNode> isOdd = node -> Integer.parseInt(node.label()) % 2 == 1;

    @Test
    void custStem() {
        TestNode tree = TestNode.empty("1");
        tree.appendPath("3", "5", "2", "1");
        tree.appendPath("7", "4");
        tree.appendPath("7", "6");

        List<String> complement = new CutStemVisitor<>(isOdd).walk(tree).result()
                .onItem()
                .transform(PrintVisitor::toString)
                .collect().asList()
                .await().indefinitely();
        Assertions.assertThat(complement)
                .containsExactly("""
                        2 (2)
                        `- 1 (1)
                        """,
                        """
                                4 (4)
                                """,
                        """
                                6 (6)
                                """);

    }
}
