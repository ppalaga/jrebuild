/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.scm;

import eu.maveniverse.maven.mima.context.Context;
import eu.maveniverse.maven.mima.context.ContextOverrides;
import eu.maveniverse.maven.mima.context.Runtime;
import java.util.List;
import java.util.stream.Collectors;
import org.assertj.core.api.Assertions;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;
import org.l2x6.jrebuild.core.dep.DependencyCollector;
import org.l2x6.jrebuild.core.dep.DependencyCollectorRequest;
import org.l2x6.jrebuild.core.dep.DependencyCollectorRequest.Builder;
import org.l2x6.jrebuild.core.dep.DependencyCollectorTest;
import org.l2x6.jrebuild.core.dep.JrebuildTestUtils;
import org.l2x6.jrebuild.core.mima.JRebuildRuntime;
import org.l2x6.jrebuild.core.mima.internal.CachingMavenModelReader;
import org.l2x6.jrebuild.core.scm.ScmRepositoryLocator.ScmInfoNode;
import org.l2x6.jrebuild.core.tree.PrintVisitor;
import org.l2x6.pom.tuner.model.Gavtc;

public class ScmRepositoryLocatorTest {
    private static final Logger log = Logger.getLogger(DependencyCollectorTest.class);

    @Test
    void scm() {
        Runtime runtime = JRebuildRuntime.getInstance();
        ContextOverrides.Builder overrides = JrebuildTestUtils.testRepo();
        try (Context context = runtime.create(overrides.build())) {
            Builder builder = DependencyCollectorRequest.builder()
                    .includeOptionalDependencies(true)
                    .rootArtifacts(Gavtc.of("org.l2x6.jrebuild.test-project:jrebuild-test-impl:0.0.1"));
            DependencyCollectorRequest re = builder.build();
            final ScmRepositoryLocator locator = new ScmRepositoryLocator(
                    context.lookup().lookup(CachingMavenModelReader.class).get()::readEffectiveModel);
            List<String> trees = DependencyCollector.collect(context, re)
                    .map(resolvedArtifact -> {
                        ScmInfoNode rootScmInfoNode = locator.newVisitor().walk(resolvedArtifact).rootNode();
                        return PrintVisitor.toString(rootScmInfoNode);
                    })
                    .peek(p -> log.infof("Scm Repos:\n%s", p))
                    .collect(Collectors.toList());
            Assertions.assertThat(trees).containsExactly(
                    """
                            https://github.com/l2x6/jrebuild-test#0.0.1 [org.l2x6.jrebuild.test-project:*:0.0.1]
                            `- https://github.com/l2x6/jrebuild-test-transitive#0.0.1 [org.l2x6.jrebuild.test-project:jrebuild-test-transitive:0.0.1:jar]
                            """);
        }

    }

}
