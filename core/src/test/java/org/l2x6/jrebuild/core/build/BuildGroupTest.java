/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.build;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.l2x6.jrebuild.api.scm.FqScmRef.AnnotatedFqScmRef;
import org.l2x6.jrebuild.api.scm.ScmRef;
import org.l2x6.jrebuild.api.scm.ScmRef.Kind;
import org.l2x6.jrebuild.api.scm.ScmRepository.AnnotatedScmRepository;
import org.l2x6.pom.tuner.model.Gav;
import org.l2x6.pom.tuner.model.Gavtc;

public class BuildGroupTest {

    @Test
    void findMainGroupId() {

        AnnotatedFqScmRef scmRef = new AnnotatedFqScmRef(new ScmRef(Kind.TAG, "1.2.3", "deadbeef"),
                new AnnotatedScmRepository("?", "git", "https://github.com/org/project.git"));
        {
            BuildGroup<AnnotatedFqScmRef> g = BuildGroup.builder(scmRef)
                    .artifact(Gavtc.of("foo:f1:1.2.3"))
                    .artifact(Gavtc.of("foo:f2:1.2.3"))
                    .artifact(Gavtc.of("bar:f1:1.2.3"))
                    .artifact(Gavtc.of("bar:f2:1.2.3"))
                    .build();
            Assertions.assertThat(g.findMainArtifact()).isEqualTo(Gav.of("bar:f:1.2.3"));
        }

        {
            BuildGroup<AnnotatedFqScmRef> g = BuildGroup.builder(scmRef)
                    .artifact(Gavtc.of("foo:f1:1.2.3"))
                    .artifact(Gavtc.of("foo:f2:1.2.3"))
                    .artifact(Gavtc.of("bar:f1:1.2.3"))
                    .artifact(Gavtc.of("baz:f2:1.2.3"))
                    .build();
            Assertions.assertThat(g.findMainArtifact()).isEqualTo(Gav.of("foo:f:1.2.3"));
        }

        {
            BuildGroup<AnnotatedFqScmRef> g = BuildGroup.builder(scmRef)
                    .artifact(Gavtc.of("foo:f1:1.2.3"))
                    .build();
            Assertions.assertThat(g.findMainArtifact()).isEqualTo(Gav.of("foo:f1:1.2.3"));
        }

    }
}
