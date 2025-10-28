/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.dep;

import eu.maveniverse.maven.mima.context.Context;
import eu.maveniverse.maven.mima.extensions.mmr.MavenModelReader;
import eu.maveniverse.maven.mima.extensions.mmr.ModelRequest;
import eu.maveniverse.maven.mima.extensions.mmr.ModelResponse;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.VersionResolutionException;
import org.l2x6.pom.tuner.model.Gav;
import org.l2x6.pom.tuner.model.Gavtcs;
import org.l2x6.pom.tuner.model.GavtcsSet;

public class ManagedGavsSelector {

    public static Set<Gavtcs> select(Context context, Gav bom, GavtcsSet filters) {
        final MavenModelReader mmr = new MavenModelReader(context);
        try {

            Artifact artifact = new DefaultArtifact(
                    bom.getGroupId(),
                    bom.getArtifactId(),
                    "",
                    "pom",
                    bom.getVersion());
            final ModelResponse response = mmr.readModel(
                    ModelRequest.builder()
                            .setArtifact(
                                    artifact)
                            .build());
            Model model = response.getEffectiveModel();

            final DependencyManagement dependencyManagement = model.getDependencyManagement();
            if (dependencyManagement != null) {
                final List<Dependency> deps = dependencyManagement.getDependencies();
                if (deps != null && !deps.isEmpty()) {
                    return Collections.unmodifiableSet(deps.stream()
                            .map(JrebuildUtils::toGavtcs)
                            .filter(filters::contains)
                            .collect(Collectors.<Gavtcs, Set<Gavtcs>> toCollection(LinkedHashSet::new)));
                }
            }
        } catch (VersionResolutionException | ArtifactResolutionException | ArtifactDescriptorException e) {
            throw new RuntimeException("Could not create effective model of " + bom, e);
        }
        return Collections.emptySet();
    }
}
