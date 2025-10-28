/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.dep;

import eu.maveniverse.maven.mima.context.Context;
import java.util.stream.Stream;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.util.graph.selector.AndDependencySelector;
import org.eclipse.aether.util.graph.selector.ExclusionDependencySelector;
import org.eclipse.aether.util.graph.selector.ScopeDependencySelector;

public class DependencyCollector {

    public static Stream<ArtifactDependencyTree> collect(Context context, DependencyCollectorRequest request) {

        return request.rootArtifacts().parallelStream()
                .map(rootGavtcs -> {

                    final Artifact rootArtifact = JrebuildUtils.toAetherArtifact(rootGavtcs);

                    org.eclipse.aether.graph.Dependency dependency = new org.eclipse.aether.graph.Dependency(rootArtifact,
                            "runtime");
                    CollectRequest collectRequest = new CollectRequest(dependency, context.remoteRepositories());

                    RepositorySystemSession repoSession = context.repositorySystemSession();
                    if (request.includeOptionalDependencies()) {
                        repoSession = new DefaultRepositorySystemSession(repoSession);
                        ((DefaultRepositorySystemSession) repoSession).setDependencySelector(new AndDependencySelector(
                                new ScopeDependencySelector("test", "provided"),
                                new ExclusionDependencySelector()));
                    }

                    try {
                        final DependencyNode rootNode = context.repositorySystem()
                                .collectDependencies(repoSession, collectRequest)
                                .getRoot();

                        return new ArtifactDependencyTree(rootGavtcs, rootNode);
                    } catch (DependencyCollectionException e) {
                        throw new RuntimeException("Could not resolve " + rootGavtcs);
                    }
                });
    }

}
