/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.dep;

import eu.maveniverse.maven.mima.context.Context;
import java.util.stream.Stream;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;

public class DependencyCollector {

    public static Stream<ArtifactDependencyTree> collect(Context context, DependencyCollectorRequest request) {

        return request.rootArtifacts().parallelStream()
                .map(rootGavtcs -> {

                    final Artifact rootArtifact = JrebuildUtils.toAetherArtifact(rootGavtcs);

                    org.eclipse.aether.graph.Dependency dependency = new org.eclipse.aether.graph.Dependency(rootArtifact,
                            "runtime");
                    CollectRequest collectRequest = new CollectRequest();
                    collectRequest.setRoot(dependency);
                    collectRequest.setRepositories(context.remoteRepositories());

                    DependencyRequest dependencyRequest = new DependencyRequest();
                    dependencyRequest.setCollectRequest(collectRequest);

                    try {
                        final DependencyNode rootNode = context.repositorySystem()
                                .resolveDependencies(context.repositorySystemSession(), dependencyRequest)
                                .getRoot();

                        return new ArtifactDependencyTree(rootGavtcs, rootNode);
                    } catch (DependencyResolutionException e) {
                        throw new RuntimeException("Could not resolve " + rootGavtcs);
                    }
                });
    }

}
