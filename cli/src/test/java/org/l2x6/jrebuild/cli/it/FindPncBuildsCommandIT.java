/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.cli.it;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.cliassured.CliAssured;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;

public class FindPncBuildsCommandIT {
    private static final Logger log = Logger.getLogger(FindPncBuildsCommandIT.class);

    @Test
    void run() throws IOException {
        Path cacheSource = Path.of("src/test/resources/pnc");
        UUID uuid = UUID.randomUUID();
        Path cacheDir = Path.of("target/" + uuid + "-cache");
        Path cacheTarget = cacheDir.resolve("pnc");
        Files.walkFileTree(cacheSource, new SimpleFileVisitor<Path>() {

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                    throws IOException {
                Path targetDir = cacheTarget.resolve(cacheSource.relativize(dir));
                Files.createDirectories(targetDir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                Files.copy(file,
                        cacheTarget.resolve(cacheSource.relativize(file)),
                        StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CliAssured.java().args(
                "-Dstdout.encoding=utf-8", "-Dstderr.encoding=utf-8",
                "-jar", "target/quarkus-app/quarkus-run.jar", "find-pnc-builds",
                "--root-artifacts=com.fasterxml.woodstox:woodstox-core:7.1.1",
                "--pnc-base-url=https://fake", // should not matter as we have everything in the cache
                // use https://orch.pnc.engineering.redhat.com/pnc-rest/v2 to refresh the cache
                "--pnc-builds-older-than=2025-12-01T10:15:30Z",
                "--cache-dir=" + cacheDir)
                .then()
                .stdout()
                .redirect(out)
                .captureAll()
                .stderr().captureAll()
                .execute()
                .assertSuccess();
        ;

        String outString = new String(out.toByteArray(), StandardCharsets.UTF_8);
        Assertions.assertThat(outString.replace("\r", "")).contains("""
                 ❌ 🟢root:root:0.0.0/null
                `- ✅ 🟢com.fasterxml.woodstox:woodstox-core:7.1.1:jar/7.1.1.redhat-00002
                   +- ✅ 👴com.fasterxml:oss-parent:68:pom/68.0.0.redhat-00004
                   `- ✅ 🟢org.codehaus.woodstox:stax2-api:4.2.2:jar/4.2.2.redhat-00003
                      `- ✅ 👴com.fasterxml:oss-parent:55:pom/55.0.0.redhat-00003
                """);
    }
}
