package org.l2x6.jrebuild.core.build;

import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.l2x6.jrebuild.core.build.ResourceMatch.IndentedLine;

public class ResourceMatchTest {

    @Test
    void toStringNoMessagesNoChildren() {
        ResourceMatch match = new ResourceMatch(
                ResourceMatchLevel.PERFECT, "some/path.jar", null, List.of());
        Assertions.assertThat(match.toString()).isEqualTo("PERFECT: some/path.jar");
    }

    @Test
    void toStringMessagesNoChildren() {
        ResourceMatch match = new ResourceMatch(
                ResourceMatchLevel.MISMATCH,
                "target/b.txt",
                List.of(
                        IndentedLine.of("--- target/a.txt"),
                        IndentedLine.of("+++ target/b.txt"),
                        IndentedLine.of("@@ -1,1 +1,1 @@"),
                        IndentedLine.of("-foo"),
                        IndentedLine.of("+bar")),
                List.of());
        Assertions.assertThat(match.toString()).isEqualTo(("""
                MISMATCH: target/b.txt:
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
                ResourceMatchLevel.MISMATCH,
                "app.war",
                List.of(IndentedLine.of("top-level note")),
                List.of(
                        new ResourceMatch(
                                ResourceMatchLevel.MISMATCH,
                                "WEB-INF/config.txt",
                                List.of(
                                        IndentedLine.of("--- a/config.txt"),
                                        IndentedLine.of("+++ b/config.txt"),
                                        IndentedLine.of("@@ -1,1 +1,1 @@"),
                                        IndentedLine.of("-v1"),
                                        IndentedLine.of("+v2")),
                                List.of()),
                        new ResourceMatch(
                                ResourceMatchLevel.MISMATCH,
                                "WEB-INF/lib/lib.jar",
                                null,
                                List.of(
                                        new ResourceMatch(
                                                ResourceMatchLevel.SUFFICIENT,
                                                "greeting.txt",
                                                List.of(
                                                        IndentedLine.of("--- a/greeting.txt"),
                                                        IndentedLine.of("+++ b/greeting.txt"),
                                                        IndentedLine.of("@@ -1,1 +1,1 @@"),
                                                        IndentedLine.of("-hello\\r"),
                                                        IndentedLine.of("+hello\\r\\n")),
                                                List.of())))));
        Assertions.assertThat(match.toString()).isEqualTo(("""
                MISMATCH: app.war:
                    top-level note
                MISMATCH: WEB-INF/config.txt:
                        --- a/config.txt
                        +++ b/config.txt
                        @@ -1,1 +1,1 @@
                        -v1
                        +v2
                MISMATCH: WEB-INF/lib/lib.jar
                SUFFICIENT: greeting.txt:
                            --- a/greeting.txt
                            +++ b/greeting.txt
                            @@ -1,1 +1,1 @@
                            -hello\\r
                            +hello\\r\\n
                """).stripTrailing());
    }
}
