/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.mima.internal;

import eu.maveniverse.maven.mima.context.Context;
import eu.maveniverse.maven.mima.extensions.mmr.MavenModelReader;
import eu.maveniverse.maven.mima.extensions.mmr.ModelRequest;
import eu.maveniverse.maven.mima.extensions.mmr.ModelResponse;
import java.util.Objects;
import org.apache.maven.model.Model;
import org.apache.maven.model.building.ModelBuilder;
import org.apache.maven.model.interpolation.StringVisitorModelInterpolator;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.impl.RemoteRepositoryManager;
import org.eclipse.aether.impl.RepositoryEventDispatcher;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.VersionResolutionException;
import org.l2x6.pom.tuner.model.Gav;

public class CachingMavenModelReader extends MavenModelReader {

    private final Context context;
    private final MavenModelReaderImpl mavenModelReaderImpl;

    public CachingMavenModelReader(Context context) {
        super(context);
        this.context = context;
        this.mavenModelReaderImpl = new MavenModelReaderImpl(
                context.repositorySystemSession(),
                context.repositorySystem(),
                context.lookup()
                        .lookup(RemoteRepositoryManager.class)
                        .orElseThrow(() -> new IllegalStateException("RemoteRepositoryManager not available")),
                context.lookup()
                        .lookup(RepositoryEventDispatcher.class)
                        .orElseThrow(() -> new IllegalStateException("RepositoryEventDispatcher not available")),
                context.lookup()
                        .lookup(ModelBuilder.class)
                        .orElseThrow(() -> new IllegalStateException("ModelBuilder not available")),
                context.lookup()
                        .lookup(StringVisitorModelInterpolator.class)
                        .orElseThrow(() -> new IllegalStateException("StringVisitorModelInterpolator not available")),
                context.remoteRepositories());
    }

    /**
     * Reads POM as {@link ModelResponse}.
     * <p>
     * Remark related to repositories: by default context "root" remote repositories will be used, unless
     * request {@link ModelRequest#getRepositories()} returns non-null value, in which case request provided
     * repositories will be used.
     */
    public ModelResponse readModel(ModelRequest request)
            throws VersionResolutionException, ArtifactResolutionException, ArtifactDescriptorException {
        Objects.requireNonNull(request, "request");
        return mavenModelReaderImpl.readModel(context.repositorySystemSession(), request);
    }

    public ModelResponse readModel(Gav gav) {
        final Artifact artifact = new DefaultArtifact(
                gav.getGroupId(),
                gav.getArtifactId(),
                "",
                "pom",
                gav.getVersion());
        try {
            return readModel(ModelRequest.builder().setArtifact(artifact).build());
        } catch (VersionResolutionException | ArtifactResolutionException | ArtifactDescriptorException e) {
            throw new RuntimeException("Could not read pom of " + gav, e);
        }
    }

    public Model readEffectiveModel(Gav gav) {
        return readModel(gav).getEffectiveModel();
    }

}
