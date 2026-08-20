/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.api.scm;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.l2x6.jrebuild.api.scm.ScmRef.Kind;
import org.l2x6.jrebuild.api.scm.ScmRepository.ScmRepositoryType;

public class FqScmRefTest {
    @Test
    void ofString() {
        Assertions.assertThat(FqScmRef.of("https://github.com/l2x6/pom-tuner.git#1.2.3")).isEqualTo(
                FqScmRef.of(new ScmRef(Kind.TAG, "1.2.3", null), ScmRepository.git("https://github.com/l2x6/pom-tuner.git")));
        Assertions.assertThat(FqScmRef.of("git:https://github.com/l2x6/pom-tuner.git#1.2.3")).isEqualTo(
                FqScmRef.of(new ScmRef(Kind.TAG, "1.2.3", null), ScmRepository.git("https://github.com/l2x6/pom-tuner.git")));
        Assertions.assertThat(FqScmRef.of("svn:https://github.com/l2x6/pom-tuner.git#1.2.3"))
                .isEqualTo(FqScmRef.of(new ScmRef(Kind.TAG, "1.2.3", null),
                        ScmRepository.of(ScmRepositoryType.svn, "https://github.com/l2x6/pom-tuner.git")));
    }
}
