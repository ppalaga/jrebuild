package org.l2x6.jrebuild.core.build.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.l2x6.jrebuild.core.build.Resource;
import org.l2x6.jrebuild.core.build.Resource.FileResource;
import org.l2x6.jrebuild.core.build.ResourceMatch;
import org.l2x6.jrebuild.core.build.ResourceMatchLevel;
import org.l2x6.jrebuild.core.build.service.ResourceMatchService.BaseResourceMatchService.TextResourceMatchService;

public class TextResourceMatchServiceTest {
    @Test
    void textResourceMatchService() throws IOException {
        ResourceMatchService service = new TextResourceMatchService();
        assertCompareTexts(service);
    }

    @Test
    void textBaseResourceMatchService() throws IOException {
        ResourceMatchService service = new ResourceMatchService.BaseResourceMatchService();
        assertCompareTexts(service);
    }

    static void assertCompareTexts(ResourceMatchService service) throws IOException {
        assertCompareText(service, "foo", "bar", new ResourceMatch(
                ResourceMatchLevel.BUILDABLE,
                "target/b.txt",
                """
                        --- target/a.txt
                        +++ target/b.txt
                        @@ -1,1 +1,1 @@
                        -foo
                        +bar
                        """.trim(),
                List.of()));
        assertCompareText(service, "foo\nbar", "foo\r\nbar", new ResourceMatch(
                ResourceMatchLevel.SUFFICIENT,
                "target/b.txt",
                """
                        --- target/a.txt
                        +++ target/b.txt
                        @@ -1,2 +1,2 @@
                        -foo\\r
                        +foo\\r\\n
                         bar
                        """.trim(),
                List.of()));
        assertCompareText(service, "foo\n", "foo\r\n", new ResourceMatch(
                ResourceMatchLevel.SUFFICIENT,
                "target/b.txt",
                """
                        --- target/a.txt
                        +++ target/b.txt
                        @@ -1,1 +1,1 @@
                        -foo\\r
                        +foo\\r\\n
                        """.trim(),
                List.of()));
        assertCompareText(service, "", "", ResourceMatchLevel.PERFECT.match("target/b.txt"));
        assertCompareText(service, "foo", "foo", ResourceMatchLevel.PERFECT.match("target/b.txt"));
        assertCompareText(service, "foo\nbar", "foo\nbar", ResourceMatchLevel.PERFECT.match("target/b.txt"));
        assertCompareText(service, "foo\rbar", "foo\rbar", ResourceMatchLevel.PERFECT.match("target/b.txt"));
        assertCompareText(service, "foo\r\nbar", "foo\r\nbar", ResourceMatchLevel.PERFECT.match("target/b.txt"));
    }

    static void assertCompareText(ResourceMatchService service, String a, String b, ResourceMatch expected) throws IOException {
        Path ap = Path.of("target/a.txt");
        Path bp = Path.of("target/b.txt");
        {
            Resource ar = Resource.of(ap.toString().replace('\\', '/'), a.getBytes(StandardCharsets.UTF_8));
            Resource br = Resource.of(bp.toString().replace('\\', '/'), b.getBytes(StandardCharsets.UTF_8));
            ResourceMatch actual = service.compare(ar, br);
            Assertions.assertThat(actual).isEqualTo(expected);
        }
        {

            Files.write(ap, a.getBytes(StandardCharsets.UTF_8));
            Files.write(bp, b.getBytes(StandardCharsets.UTF_8));

            Resource ar = new FileResource(ap, ap.toString());
            Resource br = new FileResource(bp, bp.toString());
            ResourceMatch actual = service.compare(ar, br);
            Assertions.assertThat(actual).isEqualTo(expected);
        }
    }

}
