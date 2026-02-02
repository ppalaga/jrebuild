/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.scm;

import java.util.List;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.eclipse.aether.repository.RemoteRepository;
import org.junit.jupiter.api.Test;
import org.l2x6.jrebuild.api.scm.FqScmRef;
import org.l2x6.jrebuild.api.scm.ScmRef;
import org.l2x6.jrebuild.api.scm.ScmRepository;
import org.l2x6.jrebuild.core.dep.ResolvedArtifactNode;
import org.l2x6.jrebuild.core.dep.ResolvedArtifactNode.Builder;
import org.l2x6.jrebuild.core.dep.ResolvedArtifactNode.DependencyAxis;
import org.l2x6.jrebuild.core.scm.ScmRepositoryService.ScmInfoNode;
import org.l2x6.jrebuild.core.scm.ScmRepositoryService.ScmRepositoryLocatorVisitor;
import org.l2x6.pom.tuner.model.Gav;
import org.l2x6.pom.tuner.model.Gavtc;

public class ScmRepositoryLocatorVisitorTest {
    static final Gavtc pGavtc = Gavtc.of("foo:foo-parent:1.2.3");
    static final Gavtc ch1Gavtc = Gavtc.of("foo:foo-child1:1.2.3");
    static final Gavtc ch2Gavtc = Gavtc.of("foo:foo-child2:1.2.3");
    static final Gavtc bar1Gavtc = Gavtc.of("bar:bar1:2.3.4");
    static final Gavtc bar2Gavtc = Gavtc.of("bar:bar2:2.3.4");
    static final FqScmRef foo123 = new FqScmRef(ScmRef.Kind.TAG.createRef("1.2.3", "deadbeef"),
            new ScmRepository("s", "git", "http://github.com/foo/foo.git"));
    static final FqScmRef bar234 = new FqScmRef(ScmRef.Kind.TAG.createRef("2.3.4", "c0febabe"),
            new ScmRepository("s", "git", "http://github.com/bar/bar.git"));
    static final List<RemoteRepository> repos = List.of();
    static final Map<Gav, FqScmRef> refs = Map.of(
            pGavtc.toGav(), foo123,
            ch1Gavtc.toGav(), foo123,
            ch2Gavtc.toGav(), foo123,
            bar1Gavtc.toGav(), bar234,
            bar2Gavtc.toGav(), bar234);

    private static Builder aNodeBuilder(Gavtc gavtc) {
        return new ResolvedArtifactNode.Builder(DependencyAxis.DEPENDENCY, gavtc, repos);
    }

    @Test
    void single() {

        final ResolvedArtifactNode chArtNode = aNodeBuilder(ch1Gavtc).build();

        ScmInfoNode root = visitor().walk(chArtNode).rootNode();

        Assertions.assertThat(root.buildGroup().artifacts()).containsExactly(ch1Gavtc);
        Assertions.assertThat(root.buildGroup().scmRef()).isEqualTo(foo123);
        Assertions.assertThat(root.children()).isEmpty();
    }

    @Test
    void parentChildSameRepo() {

        final ResolvedArtifactNode pArtNode = aNodeBuilder(pGavtc)
                .child(aNodeBuilder(ch1Gavtc))
                .build();

        final ScmInfoNode root = visitor().walk(pArtNode).rootNode();

        Assertions.assertThat(root.buildGroup().artifacts()).containsExactly(ch1Gavtc, pGavtc);
        Assertions.assertThat(root.buildGroup().scmRef()).isEqualTo(foo123);
        Assertions.assertThat(root.children()).isEmpty();
    }

    @Test
    void parentChildDifferentRepo() {

        final ResolvedArtifactNode pArtNode = aNodeBuilder(pGavtc)
                .child(aNodeBuilder(bar1Gavtc))
                .build();

        final ScmInfoNode root = visitor().walk(pArtNode).rootNode();

        Assertions.assertThat(root.buildGroup().artifacts()).containsExactly(pGavtc);
        Assertions.assertThat(root.buildGroup().scmRef()).isEqualTo(foo123);
        Assertions.assertThat(root.children()).hasSize(1);

        final ScmInfoNode ch = root.children().iterator().next();
        Assertions.assertThat(ch.buildGroup().artifacts()).containsExactly(bar1Gavtc);
        Assertions.assertThat(ch.buildGroup().scmRef()).isEqualTo(bar234);
        Assertions.assertThat(ch.children()).isEmpty();

    }

    @Test
    void parentChildChildDifferentRepo() {

        final ResolvedArtifactNode pArtNode = aNodeBuilder(pGavtc)
                .child(
                        aNodeBuilder(ch1Gavtc)
                                .child(aNodeBuilder(bar1Gavtc)))
                .build();

        final ScmInfoNode root = visitor().walk(pArtNode).rootNode();

        Assertions.assertThat(root.buildGroup().artifacts()).containsExactly(ch1Gavtc, pGavtc);
        Assertions.assertThat(root.buildGroup().scmRef()).isEqualTo(foo123);
        Assertions.assertThat(root.children()).hasSize(1);

        final ScmInfoNode ch = root.children().iterator().next();
        Assertions.assertThat(ch.buildGroup().artifacts()).containsExactly(bar1Gavtc);
        Assertions.assertThat(ch.buildGroup().scmRef()).isEqualTo(bar234);
        Assertions.assertThat(ch.children()).isEmpty();

    }

    @Test
    void parentChildrenDifferentRepo() {

        final ResolvedArtifactNode pArtNode = aNodeBuilder(pGavtc)
                .child(aNodeBuilder(bar1Gavtc))
                .child(aNodeBuilder(bar2Gavtc))
                .build();

        final ScmInfoNode root = visitor()
                .walk(pArtNode)
                .rootNode();

        Assertions.assertThat(root.buildGroup().artifacts()).containsExactly(pGavtc);
        Assertions.assertThat(root.buildGroup().scmRef()).isEqualTo(foo123);
        Assertions.assertThat(root.children()).hasSize(1);

        final ScmInfoNode ch = root.children().iterator().next();
        Assertions.assertThat(ch.buildGroup().artifacts()).containsExactly(bar1Gavtc, bar2Gavtc);
        Assertions.assertThat(ch.buildGroup().scmRef()).isEqualTo(bar234);
        Assertions.assertThat(ch.children()).isEmpty();

    }

    @Test
    void parentChildrenInterleavedWithChidrenFromDifferentRepo() {

        {
            final ResolvedArtifactNode pArtNode = aNodeBuilder(pGavtc)
                    .child(aNodeBuilder(bar1Gavtc))
                    .child(aNodeBuilder(ch1Gavtc))
                    .child(aNodeBuilder(bar2Gavtc))
                    .child(aNodeBuilder(ch2Gavtc))
                    .build();

            final ScmInfoNode root = visitor()
                    .walk(pArtNode)
                    .rootNode();

            Assertions.assertThat(root.buildGroup().artifacts()).containsExactly(ch1Gavtc, ch2Gavtc, pGavtc);
            Assertions.assertThat(root.buildGroup().scmRef()).isEqualTo(foo123);
            Assertions.assertThat(root.children()).hasSize(1);

            final ScmInfoNode ch = root.children().iterator().next();
            Assertions.assertThat(ch.buildGroup().artifacts()).containsExactly(bar1Gavtc, bar2Gavtc);
            Assertions.assertThat(ch.buildGroup().scmRef()).isEqualTo(bar234);
            Assertions.assertThat(ch.children()).isEmpty();
        }

        {
            final ResolvedArtifactNode pArtNode = aNodeBuilder(pGavtc)
                    .child(aNodeBuilder(bar1Gavtc))
                    .child(aNodeBuilder(ch1Gavtc).child(aNodeBuilder(ch2Gavtc)))
                    .child(aNodeBuilder(bar2Gavtc))
                    .build();

            final ScmInfoNode root = visitor()
                    .walk(pArtNode)
                    .rootNode();

            Assertions.assertThat(root.buildGroup().artifacts()).containsExactly(ch1Gavtc, ch2Gavtc, pGavtc);
            Assertions.assertThat(root.buildGroup().scmRef()).isEqualTo(foo123);
            Assertions.assertThat(root.children()).hasSize(1);

            final ScmInfoNode ch = root.children().iterator().next();
            Assertions.assertThat(ch.buildGroup().artifacts()).containsExactly(bar1Gavtc, bar2Gavtc);
            Assertions.assertThat(ch.buildGroup().scmRef()).isEqualTo(bar234);
            Assertions.assertThat(ch.children()).isEmpty();
        }
        {
            final ResolvedArtifactNode pArtNode = aNodeBuilder(pGavtc)
                    .child(aNodeBuilder(bar1Gavtc).child(aNodeBuilder(bar2Gavtc)))
                    .child(aNodeBuilder(ch1Gavtc).child(aNodeBuilder(ch2Gavtc)))
                    .build();

            final ScmInfoNode root = visitor()
                    .walk(pArtNode)
                    .rootNode();

            Assertions.assertThat(root.buildGroup().artifacts()).containsExactly(ch1Gavtc, ch2Gavtc, pGavtc);
            Assertions.assertThat(root.buildGroup().scmRef()).isEqualTo(foo123);
            Assertions.assertThat(root.children()).hasSize(1);

            final ScmInfoNode ch = root.children().iterator().next();
            Assertions.assertThat(ch.buildGroup().artifacts()).containsExactly(bar1Gavtc, bar2Gavtc);
            Assertions.assertThat(ch.buildGroup().scmRef()).isEqualTo(bar234);
            Assertions.assertThat(ch.children()).isEmpty();
        }

    }

    @Test
    void parentTwoLevelsOfChildrenFromTheSameRepo() {

        final ResolvedArtifactNode pArtNode = aNodeBuilder(pGavtc)
                .child(aNodeBuilder(ch1Gavtc).child(aNodeBuilder(ch2Gavtc)))
                .build();

        final ScmInfoNode root = visitor()
                .walk(pArtNode)
                .rootNode();

        Assertions.assertThat(root.buildGroup().artifacts()).containsExactly(ch1Gavtc, ch2Gavtc, pGavtc);
        Assertions.assertThat(root.buildGroup().scmRef()).isEqualTo(foo123);
        Assertions.assertThat(root.children()).isEmpty();

    }

    static ScmRepositoryLocatorVisitor visitor() {
        return new ScmRepositoryLocatorVisitor((gav, stack) -> refs.get(gav));
    }
}
