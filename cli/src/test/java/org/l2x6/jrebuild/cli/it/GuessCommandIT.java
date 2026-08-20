/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.cli.it;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.assertj.core.api.Assertions;
import org.cliassured.CliAssured;
import org.cliassured.CommandResult;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;

public class GuessCommandIT {
    private static final Logger log = Logger.getLogger(GuessCommandIT.class);

    @Test
    void run() throws IOException {
        //Path output = Path.of("target/GuessCommandIT/out.yaml");
        Path cacheDir = Path.of("target/GuessCommandIT/cache");
        CommandResult result = CliAssured.java().args(
                "-Dstdout.encoding=utf-8", "-Dstderr.encoding=utf-8",
                "-jar", "target/quarkus-app/quarkus-run.jar",
                "guess",
                "--cache-dir=" + cacheDir,
                "https://github.com/l2x6/pom-tuner.git#4.10.0")
                .then()
                .stdout()
                .captureAll()
                .stderr().captureAll()
                .execute()
                .assertSuccess();

        Assertions.assertThat(new String(result.stdout().bytes(), StandardCharsets.UTF_8)).isEqualTo("""
                ---
                buildGroup:
                  fqScmRef:
                    scmRef:
                      kind: TAG
                      name: 4.10.0
                    repository:
                      type: git
                      uri: https://github.com/l2x6/pom-tuner.git
                  artifacts:
                    - org.l2x6.pom-tuner:pom-tuner:4.10.0:jar
                    - org.l2x6.pom-tuner:pom-tuner:4.10.0:jar:javadoc
                    - org.l2x6.pom-tuner:pom-tuner:4.10.0:jar:sources
                    - org.l2x6.pom-tuner:pom-tuner:4.10.0:pom
                    - org.l2x6.pom-tuner:pom-tuner-parent:4.10.0:pom
                    - org.l2x6.pom-tuner:pom-tuner-tests:4.10.0:jar
                    - org.l2x6.pom-tuner:pom-tuner-tests:4.10.0:pom
                os: LINUX
                arch: amd64
                buildTool:
                  - buildTool: maven_wrapper
                java:
                  - distro: Temurin
                    version: 11.0.25
                  - distro: Temurin
                    version: 11
                  - distro: Temurin
                    version: 8
                """);
    }
}
