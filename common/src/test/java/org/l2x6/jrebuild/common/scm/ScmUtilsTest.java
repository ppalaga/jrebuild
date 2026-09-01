/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.common.scm;

import org.junit.jupiter.api.Test;
import org.l2x6.jrebuild.api.scm.ScmRepository.ScmRepositoryType;

import static org.assertj.core.api.Assertions.assertThat;

public class ScmUtilsTest {
    @Test
    void toNormalizedHttpsAnnotatedScmRepository() {
        assertNormalizeScmUri("scm:git:git@gitlab.ow2.org:ow2-lifecycle/ow2-parent-pom.git",
                "https://gitlab.ow2.org/ow2-lifecycle/ow2-parent-pom.git");
    }

    static void assertNormalizeScmUri(String actual, String expected) {
        assertThat(
                ScmUtils.toNormalizedHttpsAnnotatedScmRepository(
                        "?",
                        ScmRepositoryType.git,
                        actual)
                        .uri())
                .isEqualTo(expected);
    }

    @Test
    void normalizeScmUri() {
        assertNormalizeScmUri("ssh://git@github.com/org/repo", "https://github.com/org/repo.git");
        assertNormalizeScmUri("scm:git:ssh://git@github.com/eclipse-ee4j/jaxb-stax-ex.git",
                "https://github.com/eclipse-ee4j/jaxb-stax-ex.git");
        assertNormalizeScmUri("git://git@github.com:owner/repo.git", "https://github.com/owner/repo.git");
        assertNormalizeScmUri("git@github.com:smallrye/smallrye-stork.git", "https://github.com/smallrye/smallrye-stork.git");
        assertNormalizeScmUri("https://github.com/trailing-dot.", "https://github.com/trailing-dot.git");
        assertNormalizeScmUri("file:///home/user/projects/foo/bar/.git/", "file:///home/user/projects/foo/bar/.git/");
        assertNormalizeScmUri("https://github.com/org/repo.git", "https://github.com/org/repo.git");
        assertNormalizeScmUri("https://github.com/org/repo", "https://github.com/org/repo.git");
        assertNormalizeScmUri("file:///C:/Program Files/Some App/app.exe", "file:///C:/Program Files/Some App/app.exe");
        assertNormalizeScmUri("C:\\Program Files\\Some App\\app.exe", "C:\\Program Files\\Some App\\app.exe");
        assertNormalizeScmUri("git+ssh://git@github.com:owner/repo.git", "https://github.com/owner/repo.git");
        assertNormalizeScmUri("ssh://git@github.com:owner/repo.git", "https://github.com/owner/repo.git");
        assertNormalizeScmUri("git@github.com:quarkusio/quarkus.git", "https://github.com/quarkusio/quarkus.git");
    }

}
