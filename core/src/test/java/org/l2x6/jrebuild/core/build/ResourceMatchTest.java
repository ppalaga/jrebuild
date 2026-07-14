package org.l2x6.jrebuild.core.build;

import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class ResourceMatchTest {

    @Test
    void toStringNoMessagesNoChildren() {
        ResourceMatch match = new ResourceMatch(
                "some/path.jar", ResourceMatchLevel.PERFECT, null, List.of());
        Assertions.assertThat(match.toString()).isEqualTo("PERFECT: some/path.jar");
    }

    @Test
    void toStringMessagesNoChildren() {
        ResourceMatch match = new ResourceMatch(
                "target/b.txt",
                ResourceMatchLevel.BUILDABLE,
                """
                        --- target/a.txt
                        +++ target/b.txt
                        @@ -1,1 +1,1 @@
                        -foo
                        +bar
                        """,
                List.of());
        Assertions.assertThat(match.toString()).isEqualTo(("""
                BUILDABLE: target/b.txt:
                    --- target/a.txt
                    +++ target/b.txt
                    @@ -1,1 +1,1 @@
                    -foo
                    +bar
                """).stripTrailing());
    }

    @Test
    void toStringMessagesAndTwoLevelsOfChildren() {
        ResourceMatch match = new ResourceMatch(
                "app.war",
                ResourceMatchLevel.BUILDABLE,
                "top-level note",
                List.of(
                        new ResourceMatch(
                                "WEB-INF/config.txt",
                                ResourceMatchLevel.BUILDABLE,
                                """
                                        --- a/config.txt
                                        +++ b/config.txt
                                        @@ -1,1 +1,1 @@
                                        -v1
                                        +v2
                                        """,
                                List.of()),
                        new ResourceMatch(
                                "WEB-INF/lib/lib.jar",
                                ResourceMatchLevel.BUILDABLE,
                                null,
                                List.of(
                                        new ResourceMatch(
                                                "greeting.txt",
                                                ResourceMatchLevel.SUFFICIENT,
                                                """
                                                        --- a/greeting.txt
                                                        +++ b/greeting.txt
                                                        @@ -1,1 +1,1 @@
                                                        -hello\\r
                                                        +hello\\r\\n
                                                        """,
                                                List.of())))));
        Assertions.assertThat(match.toString()).isEqualTo(("""
                BUILDABLE: app.war:
                    top-level note
                BUILDABLE: WEB-INF/config.txt:
                        --- a/config.txt
                        +++ b/config.txt
                        @@ -1,1 +1,1 @@
                        -v1
                        +v2
                BUILDABLE: WEB-INF/lib/lib.jar
                SUFFICIENT: greeting.txt:
                            --- a/greeting.txt
                            +++ b/greeting.txt
                            @@ -1,1 +1,1 @@
                            -hello\\r
                            +hello\\r\\n
                """).stripTrailing());
    }
}
