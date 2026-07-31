/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild
 *                                 project contributors as indicated by the @author tags
 *                                 SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.api.scm;

import java.util.Optional;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class ScmRepositoryTest {

    @Test
    void lastPathSegmentGitHttpsWithDotGit() {
        ScmRepository.AnnotatedScmRepository repo = new ScmRepository.AnnotatedScmRepository("src", "git",
                "https://github.com/apache/camel.git");
        Assertions.assertThat(repo.lastPathSegment()).isEqualTo(Optional.of("camel"));
    }

    @Test
    void lastPathSegmentGitHttpsWithoutDotGit() {
        ScmRepository.AnnotatedScmRepository repo = new ScmRepository.AnnotatedScmRepository("src", "git",
                "https://github.com/apache/camel");
        Assertions.assertThat(repo.lastPathSegment()).isEqualTo(Optional.of("camel"));
    }

    @Test
    void lastPathSegmentGitSshWithDotGit() {
        ScmRepository.AnnotatedScmRepository repo = new ScmRepository.AnnotatedScmRepository("src", "git",
                "git@github.com:apache/camel.git");
        Assertions.assertThat(repo.lastPathSegment()).isEqualTo(Optional.of("camel"));
    }

    @Test
    void lastPathSegmentGitSshWithoutDotGit() {
        ScmRepository.AnnotatedScmRepository repo = new ScmRepository.AnnotatedScmRepository("src", "git",
                "git@github.com:apache/camel");
        Assertions.assertThat(repo.lastPathSegment()).isEqualTo(Optional.of("camel"));
    }

    @Test
    void lastPathSegmentNonGitType() {
        ScmRepository.AnnotatedScmRepository repo = new ScmRepository.AnnotatedScmRepository("src", "svn",
                "https://svn.example.org/repos/myproject/trunk");
        Assertions.assertThat(repo.lastPathSegment()).isEqualTo(Optional.of("trunk"));
    }

    @Test
    void lastPathSegmentGitNoSlashInPath() {
        ScmRepository.AnnotatedScmRepository repo = new ScmRepository.AnnotatedScmRepository("src", "git",
                "https://example.org/repo.git");
        Assertions.assertThat(repo.lastPathSegment()).isEqualTo(Optional.of("repo"));
    }

}
