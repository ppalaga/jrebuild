package org.l2x6.jrebuild.core.build.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.l2x6.jrebuild.api.util.JrebuildUtils;
import org.l2x6.jrebuild.core.build.Resource;
import org.l2x6.jrebuild.core.build.ResourceMatch;
import org.l2x6.jrebuild.core.build.ResourceMatch.IndentedLine;
import org.l2x6.jrebuild.core.build.ResourceMatchLevel;
import org.l2x6.jrebuild.core.build.service.ResourceMatchService.BaseResourceMatchService;
import org.l2x6.jrebuild.core.build.service.ResourceMatchService.BaseResourceMatchService.ZipResourceMatchService;

public class ZipResourceMatchServiceTest {

    @Test
    void zipResourceMatchService() throws IOException {
        ResourceMatchService service = new ZipResourceMatchService(new BaseResourceMatchService());
        assertCompareZipFiles(service);
    }

    @Test
    void zipBaseResourceMatchService() throws IOException {
        ResourceMatchService service = new BaseResourceMatchService();
        assertCompareZipFiles(service);
    }

    static void assertCompareZipFiles(ResourceMatchService service) throws IOException {
        byte[] jar1 = buildJar("Hello World");
        byte[] jar2 = buildJar("Hello Changed World");
        byte[] war1 = buildWar("version=1.0", jar1);
        byte[] war2 = buildWar("version=2.0", jar2);

        assertCompareSameZipFile(service, "target/zip-test/jar1.jar", jar1);
        assertCompareSameZipFile(service, "target/zip-test/war1.war", war1);

        assertCompareZipFile(
                service,
                "target/zip-test/jar1.jar",
                "target/zip-test/jar2.jar",
                jar1, jar2,
                new ResourceMatch(
                        ResourceMatchLevel.BUILDABLE,
                        "target/zip-test/jar2.jar",
                        null,
                        List.of(
                                new ResourceMatch(
                                        ResourceMatchLevel.BUILDABLE,
                                        "target/zip-test/jar2.jar!greeting.txt",
                                        split("""
                                                --- target/zip-test/jar1.jar!greeting.txt
                                                +++ target/zip-test/jar2.jar!greeting.txt
                                                @@ -1,1 +1,1 @@
                                                -Hello World
                                                +Hello Changed World
                                                """),
                                        List.of()))));

        assertCompareZipFile(
                service,
                "target/zip-test/war1.war",
                "target/zip-test/war2.war",
                war1, war2,
                new ResourceMatch(
                        ResourceMatchLevel.BUILDABLE,
                        "target/zip-test/war2.war",
                        null,
                        List.of(
                                new ResourceMatch(
                                        ResourceMatchLevel.BUILDABLE,
                                        "target/zip-test/war2.war!WEB-INF/classes/config.txt",
                                        split("""
                                                --- target/zip-test/war1.war!WEB-INF/classes/config.txt
                                                +++ target/zip-test/war2.war!WEB-INF/classes/config.txt
                                                @@ -1,1 +1,1 @@
                                                -version=1.0
                                                +version=2.0
                                                """),
                                        List.of()),
                                new ResourceMatch(
                                        ResourceMatchLevel.BUILDABLE,
                                        "target/zip-test/war2.war!WEB-INF/lib/lib.jar",
                                        null,
                                        List.of(
                                                new ResourceMatch(
                                                        ResourceMatchLevel.BUILDABLE,
                                                        "target/zip-test/war2.war!WEB-INF/lib/lib.jar!greeting.txt",
                                                        split("""
                                                                --- target/zip-test/war1.war!WEB-INF/lib/lib.jar!greeting.txt
                                                                +++ target/zip-test/war2.war!WEB-INF/lib/lib.jar!greeting.txt
                                                                @@ -1,1 +1,1 @@
                                                                -Hello World
                                                                +Hello Changed World
                                                                """),
                                                        List.of()))))));
    }

    static void assertCompareSameZipFile(
            ResourceMatchService service, String path, byte[] zipBytes) throws IOException {
        Path p = Path.of(path);
        Files.createDirectories(p.getParent());
        Files.write(p, zipBytes);
        {
            Resource ar = Resource.of(path, zipBytes);
            Resource br = Resource.of(path, zipBytes);
            ResourceMatch actual = service.compare(ar, br);
            Assertions.assertThat(actual.level()).isEqualTo(ResourceMatchLevel.PERFECT);
            Assertions.assertThat(actual.path()).isEqualTo(path);
        }
        {
            Resource ar = Resource.of(p);
            Resource br = Resource.of(p);
            ResourceMatch actual = service.compare(ar, br);
            Assertions.assertThat(actual.level()).isEqualTo(ResourceMatchLevel.PERFECT);
            Assertions.assertThat(actual.path()).isEqualTo(p.toString().replace('\\', '/'));
        }
    }

    static void assertCompareZipFile(
            ResourceMatchService service,
            String a, String b,
            byte[] aBytes, byte[] bBytes,
            ResourceMatch expected) throws IOException {
        Path ap = Path.of(a);
        Path bp = Path.of(b);
        Files.createDirectories(ap.getParent());
        Files.write(ap, aBytes);
        Files.write(bp, bBytes);
        {
            Resource ar = Resource.of(ap.toString().replace('\\', '/'), aBytes);
            Resource br = Resource.of(bp.toString().replace('\\', '/'), bBytes);
            ResourceMatch actual = service.compare(ar, br);
            try {
                Assertions.assertThat(actual).isEqualTo(expected);
            } catch (AssertionError e) {
                Files.writeString(Path.of("target/match-expected.txt"), expected.toString());
                Files.writeString(Path.of("target/match-actual.txt"), actual.toString());
                throw e;
            }
        }
        {
            Resource ar = Resource.of(ap);
            Resource br = Resource.of(bp);
            ResourceMatch actual = service.compare(ar, br);
            try {
                Assertions.assertThat(actual).isEqualTo(expected);
            } catch (AssertionError e) {
                Files.writeString(Path.of("target/match-expected.txt"), expected.toString());
                Files.writeString(Path.of("target/match-actual.txt"), actual.toString());
                throw e;
            }
        }
    }

    static byte[] buildJar(String greetingContent) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("greeting.txt"));
            zos.write(greetingContent.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    static byte[] buildWar(String configContent, byte[] embeddedJar) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("WEB-INF/classes/config.txt"));
            zos.write(configContent.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("WEB-INF/lib/lib.jar"));
            zos.write(embeddedJar);
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    static List<IndentedLine> split(String string) {
        return JrebuildUtils.lines(string)
                .map(l -> IndentedLine.parse(l))
                .toList();
    }
}
