/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.dep.tree;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import org.l2x6.pom.tuner.model.Gav;
import org.l2x6.pom.tuner.model.Gavtcs;
import org.l2x6.pom.tuner.model.GavtcsPattern;

@JsonSerialize
public record DependencyTreeRequest(
        /** The root directory of a Maven source tree if we are analyzing a source tree; otherwise {@code null} */
        Path projectDirectory,
        Gav rootBom,
        List<GavtcsPattern> rootBomIncludes,
        Collection<Gavtcs> rootArtifacts,
        List<GavtcsPattern> excludes,
        Collection<Gav> additionalBoms,
        Collection<String> activeProfiles,
        Collection<String> mavenRepositories,
        Path mavenSettingsXmlPath) {
}
