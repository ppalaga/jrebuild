/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild
 *                                 project contributors as indicated by the @author tags
 *                                 SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.dep;

import java.util.List;
import org.assertj.core.api.Assertions;
import org.eclipse.aether.repository.RemoteRepository;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;
import org.l2x6.jrebuild.core.dep.ResolvedArtifactNode.DependencyAxis;
import org.l2x6.pom.tuner.model.Gavtc;

public class ResolvedArtifactNodeTest {
    private static final Logger log = Logger.getLogger(ResolvedArtifactNodeTest.class);

    @Test
    void equals() {
        ResolvedArtifactNode.Builder fooBuilder1 = builder("org.foo:foo:1.2.3", "org.foo:foo-child:1.2.3");

        Assertions.assertThat(fooBuilder1).isEqualTo(fooBuilder1);
        Assertions.assertThat(fooBuilder1.hashCode()).isEqualTo(fooBuilder1.hashCode());

        ResolvedArtifactNode.Builder fooBuilder2 = builder("org.foo:foo:1.2.3", "org.foo:foo-child:1.2.3");
        Assertions.assertThat(fooBuilder1).isEqualTo(fooBuilder2);
        Assertions.assertThat(fooBuilder1.hashCode()).isEqualTo(fooBuilder2.hashCode());

        ResolvedArtifactNode.Builder barBuilder = builder("org.foo:bar:1.2.3");
        ResolvedArtifactNode.Builder bazBuilder = builder("org.foo:baz:4.5.6");
        Assertions.assertThat(barBuilder).isNotEqualTo(bazBuilder);
        Assertions.assertThat(barBuilder.hashCode()).isNotEqualTo(bazBuilder.hashCode());

        Assertions.assertThat(barBuilder.build()).isNotEqualTo(bazBuilder.build());
        Assertions.assertThat(barBuilder.build().hashCode()).isNotEqualTo(bazBuilder.build().hashCode());

        /* Builders hash and equals do not consider children */
        ResolvedArtifactNode.Builder fooBuilder3 = builder("org.foo:foo:1.2.3", "org.foo:foo-child:1.2.3",
                "org.foo:bar-child:2.3.4");
        Assertions.assertThat(fooBuilder1).isEqualTo(fooBuilder3);
        Assertions.assertThat(fooBuilder1.hashCode()).isEqualTo(fooBuilder3.hashCode());

        /* Node.equals() and Node.hashCode() */
        final ResolvedArtifactNode foo1 = fooBuilder1.build();
        Assertions.assertThat(foo1).isEqualTo(fooBuilder1.build());
        Assertions.assertThat(foo1.hashCode()).isEqualTo(fooBuilder1.build().hashCode());

        Assertions.assertThat(foo1).isEqualTo(fooBuilder2.build());
        Assertions.assertThat(foo1.hashCode()).isEqualTo(fooBuilder2.build().hashCode());

        /* Node.equals() and Node.hashCode should consider children */
        Assertions.assertThat(foo1).isNotEqualTo(fooBuilder3.build());
        Assertions.assertThat(foo1.hashCode()).isNotEqualTo(fooBuilder3.build().hashCode());

    }

    private ResolvedArtifactNode.Builder builder(String parent, String... children) {
        final Gavtc gav = Gavtc.of(parent);
        List<RemoteRepository> repos = List.of(eu.maveniverse.maven.mima.context.ContextOverrides.CENTRAL);
        ResolvedArtifactNode.Builder builder = new ResolvedArtifactNode.Builder(DependencyAxis.DEPENDENCY, gav, repos);

        for (String child : children) {
            builder.child(new ResolvedArtifactNode.Builder(DependencyAxis.DEPENDENCY, Gavtc.of(child), repos));
        }
        return builder;
    }

}
