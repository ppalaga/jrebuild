/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.dep;

import org.apache.maven.model.Dependency;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.l2x6.pom.tuner.model.Gavtcs;

public class JrebuildUtils {

    public static Gavtcs toGavtcs(Dependency dep) {
        return new Gavtcs(dep.getGroupId(), dep.getArtifactId(), dep.getVersion(), dep.getType(), dep.getClassifier(), null);
    }

    public static Artifact toAetherArtifact(Gavtcs dep) {
        // String groupId, String artifactId, String classifier, String extension, String version
        return new DefaultArtifact(dep.getGroupId(), dep.getArtifactId(), dep.getClassifier(), dep.getType(), dep.getVersion());
    }
}
