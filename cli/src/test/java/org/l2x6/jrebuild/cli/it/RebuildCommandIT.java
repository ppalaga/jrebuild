/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.cli.it;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.cliassured.CliAssured;
import org.cliassured.CommandResult;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;

public class RebuildCommandIT {
    private static final Logger log = Logger.getLogger(RebuildCommandIT.class);

    @Test
    void run() throws IOException {
        Path cacheDir = Path.of("target/RebuildCommandIT/cache");
        Path buildReportsDir = Path.of("target/RebuildCommandIT/build-reports");
        CommandResult result = CliAssured.java().args(
                "-Dstdout.encoding=utf-8", "-Dstderr.encoding=utf-8",
                "-jar", "target/quarkus-app/quarkus-run.jar",
                "rebuild",
                "--cache-dir=" + cacheDir,
                "--build-reports-dir=" + buildReportsDir,
                "src/test/resources/RebuildCommandIT/pom-tuner-build-request.yaml")
                .then()
                .stdout()
                .captureAll()
                .stderr().captureAll()
                .execute()
                .assertSuccess();

        Path reportParent = buildReportsDir.resolve("github.com/l2x6/pom-tuner/4.10.0");
        List<Path> reportFiles = Files.list(reportParent).filter(Files::isRegularFile).toList();
        Assertions.assertThat(reportFiles).hasSize(1);
        Path reportPath = reportFiles.get(0);
        Assertions.assertThat(reportPath).content().contains("""
                reproducibility:
                  overall: SUFFICIENT
                  poms: PERFECT
                  jars: PERFECT
                  sources: PERFECT
                  javadocs: SUFFICIENT
                buildStart:
                """.trim());

        Assertions.assertThat(new String(result.stderr().bytes(), StandardCharsets.UTF_8).replace("\r", "")).contains("""
                git:https://github.com/l2x6/pom-tuner.git#4.10.0@null [org.l2x6.pom-tuner:pom-tuner[-(parent|tests)]:4.10.0]:

                    poms: PERFECT
                    jars: PERFECT
                    sources: PERFECT
                    javadocs: SUFFICIENT
                    -------
                    overall: SUFFICIENT
                """.trim());
    }
}
