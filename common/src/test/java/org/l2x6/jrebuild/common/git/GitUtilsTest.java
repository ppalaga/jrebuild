/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.common.git;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class GitUtilsTest {

    @Test
    void uriToFileName() {
        assertThat(GitUtils.uriToFileName("https://github.com/path/to/report.pdf?download=1#section"))
                .isEqualTo("github.com-path-to-report.pdf-download-1-section");
        assertThat(GitUtils.uriToFileName("https://github.com/org/repo.git")).isEqualTo("github.com-org-repo");
        assertThat(GitUtils.uriToFileName("file:///C:/Program Files/Some App/app.exe"))
                .isEqualTo("C-Program-Files-Some-App-app.exe");
        assertThat(GitUtils.uriToFileName("C:\\Program Files\\Some App\\app.exe"))
                .isEqualTo("C-Program-Files-Some-App-app.exe");
        assertThat(GitUtils.uriToFileName("git+ssh://git@github.com:owner/repo.git"))
                .isEqualTo("github.com-owner-repo");
        assertThat(GitUtils.uriToFileName("https://example.com/trailing-dot.")).isEqualTo("example.com-trailing-dot");
        assertThat(GitUtils.uriToFileName("git@github.com:quarkusio/quarkus.git"))
                .isEqualTo("github.com-quarkusio-quarkus");
    }

    @Test
    void uriToFilePath() {
        assertThat(GitUtils.uriToFilePath("file:///home/user/projects/foo/bar/.git/"))
                .isEqualTo(Path.of("home/user/projects/foo/bar"));
        assertThat(GitUtils.uriToFilePath("https://github.com/path/to/report.pdf?download=1#section"))
                .isEqualTo(Path.of("github.com/path/to/report.pdf-download-1-section"));
        assertThat(GitUtils.uriToFilePath("https://github.com/org/repo.git"))
                .isEqualTo(Path.of("github.com/org/repo"));
        assertThat(GitUtils.uriToFilePath("file:///C:/Program Files/Some App/app.exe"))
                .isEqualTo(Path.of("C/Program-Files/Some-App/app.exe"));
        assertThat(GitUtils.uriToFilePath("C:\\Program Files\\Some App\\app.exe"))
                .isEqualTo(Path.of("C/Program-Files/Some-App/app.exe"));
        assertThat(GitUtils.uriToFilePath("git+ssh://git@github.com:owner/repo.git"))
                .isEqualTo(Path.of("github.com/owner/repo"));
        assertThat(GitUtils.uriToFilePath("https://example.com/trailing-dot."))
                .isEqualTo(Path.of("example.com/trailing-dot"));
        assertThat(GitUtils.uriToFilePath("git@github.com:quarkusio/quarkus.git"))
                .isEqualTo(Path.of("github.com/quarkusio/quarkus"));
    }

    @Test
    void toNormalizedHttpsUri() {
        assertThat(GitUtils.toNormalizedHttpsUri("ssh://git@github.com/eclipse-ee4j/jaxb-stax-ex.git"))
                .isEqualTo("https://github.com/eclipse-ee4j/jaxb-stax-ex.git");
        assertThat(GitUtils.toNormalizedHttpsUri("//github.com/org/repo/foo"))
                .isEqualTo("https://github.com/org/repo.git");
        assertThat(GitUtils.toNormalizedHttpsUri("//github.com/org/repo"))
                .isEqualTo("https://github.com/org/repo.git");
        assertThat(GitUtils.toNormalizedHttpsUri("ssh://git@github.com/org/repo"))
                .isEqualTo("https://github.com/org/repo.git");
        assertThat(GitUtils.toNormalizedHttpsUri("https://github.com/trailing-dot."))
                .isEqualTo("https://github.com/trailing-dot.git");
        assertThat(GitUtils.toNormalizedHttpsUri("file:///home/user/projects/foo/bar/.git/"))
                .isEqualTo("file:///home/user/projects/foo/bar/.git/");
        assertThat(GitUtils.toNormalizedHttpsUri("https://github.com/org/repo.git"))
                .isEqualTo("https://github.com/org/repo.git");
        assertThat(GitUtils.toNormalizedHttpsUri("https://github.com/org/repo"))
                .isEqualTo("https://github.com/org/repo.git");
        assertThat(GitUtils.toNormalizedHttpsUri("file:///C:/Program Files/Some App/app.exe"))
                .isEqualTo("file:///C:/Program Files/Some App/app.exe");
        assertThat(GitUtils.toNormalizedHttpsUri("C:\\Program Files\\Some App\\app.exe"))
                .isEqualTo("C:\\Program Files\\Some App\\app.exe");
        assertThat(GitUtils.toNormalizedHttpsUri("git+ssh://git@github.com:owner/repo.git"))
                .isEqualTo("https://github.com/owner/repo.git");
        assertThat(GitUtils.toNormalizedHttpsUri("ssh://git@github.com:owner/repo.git"))
                .isEqualTo("https://github.com/owner/repo.git");
        assertThat(GitUtils.toNormalizedHttpsUri("git://git@github.com:owner/repo.git"))
                .isEqualTo("https://github.com/owner/repo.git");
        assertThat(GitUtils.toNormalizedHttpsUri("git@github.com:quarkusio/quarkus.git"))
                .isEqualTo("https://github.com/quarkusio/quarkus.git");
    }

}
