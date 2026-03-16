/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.mvn;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.l2x6.pom.tuner.model.Gavtc;

public class MavenUtils {
    private MavenUtils() {
    }

    public static Path resolveArtifact(
            Path localRepository,
            Gavtc gavtc,
            List<RemoteRepository> repositories,
            RepositorySystem repoSystem,
            RepositorySystemSession repoSession) {

        Objects.requireNonNull(gavtc.getType(), "gavtc.getType()");

        final String relativeJarPath = gavtc.getRepositoryPath();
        final Path localPath = localRepository.resolve(relativeJarPath);
        if (Files.isRegularFile(localPath)) {
            return localPath;
        }
        final org.eclipse.aether.artifact.Artifact aetherArtifact = new DefaultArtifact(
                gavtc.getGroupId(),
                gavtc.getArtifactId(),
                gavtc.getClassifier(),
                gavtc.getType(),
                gavtc.getVersion());

        final ArtifactRequest req = new ArtifactRequest().setRepositories(repositories).setArtifact(aetherArtifact);
        ArtifactResult resolutionResult;
        try {
            resolutionResult = repoSystem.resolveArtifact(repoSession, req);
        } catch (ArtifactResolutionException e) {
            throw new RuntimeException("Artifact " + aetherArtifact + " could not be resolved.", e);
        }
        return resolutionResult.getArtifact().getFile().toPath();

    }

}
