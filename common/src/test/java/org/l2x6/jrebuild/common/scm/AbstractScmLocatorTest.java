/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.common.scm;

import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.l2x6.jrebuild.api.scm.ScmRepository;
import org.l2x6.pom.tuner.model.Gav;

public class AbstractScmLocatorTest {
    @Test
    void formatters() {
        ScmRepository repo = ScmRepository.of("? git https://github.com/quarkusio/quarkus.git");

        List<String> tags = AbstractScmLocator.VERSION_TO_TAG_FORMATTERS.stream()
                .map(f -> f.apply(repo, Gav.of("foo:foo-core:1.2.3")))
                .toList();

        Assertions.assertThat(tags).containsExactly(
                "1.2.3",
                "foo-core-1.2.3",
                "foo-core-1_2_3",
                "1.2.3-RELEASE",
                "quarkus-1.2.3",
                "v1.2.3",
                "v_1.2.3",
                "r1.2.3",
                "V1_2_3",
                "rel/1.2.3",
                "rel/foo-core-1.2.3",
                "rel/quarkus-1.2.3",
                "QUARKUS_1_2_3",
                "QUARKUS-1_2_3");
    }

    @Test
    void lastPathSegment() {
        Assertions
                .assertThat(ScmRepository.of("? git git@github.com:quarkusio/quarkus.git").lastPathSegment()
                        .get())
                .isEqualTo("quarkus");
        Assertions
                .assertThat(
                        ScmRepository.of("? git https://github.com/quarkusio/quarkus.git").lastPathSegment()
                                .get())
                .isEqualTo("quarkus");
    }
}
