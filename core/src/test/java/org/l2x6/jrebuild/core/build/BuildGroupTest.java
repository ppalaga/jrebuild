package org.l2x6.jrebuild.core.build;

import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.l2x6.jrebuild.core.build.BuildGroup.CharNode;

public class BuildGroupTest {

    @Test
    void charNode() {
        assertCharNode("[ba[r|z]|foo]", "foo", "bar", "baz");
        assertCharNode("foo", "foo");
        assertCharNode("foo-[1|2]", "foo-1", "foo-2");
        assertCharNode("foo-[1234|2456]", "foo-1234", "foo-2456");
    }

    static void assertCharNode(String expected, String... strings) {
        CharNode root = CharNode.root();

        Stream.of(strings).forEach(root::add);

        StringBuilder sb = new StringBuilder();
        root.append(sb);
        Assertions.assertThat(sb.toString()).isEqualTo(expected);

    }
}
