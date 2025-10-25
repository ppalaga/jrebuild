/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.dep;

import java.nio.file.Path;
import java.util.Collection;
import org.l2x6.pom.tuner.model.Gav;
import org.l2x6.pom.tuner.model.Gavtcs;

public record DependencyCollectorRequest(
        /** The root directory of a Maven source tree if we are analyzing a source tree; otherwise {@code null} */
        Path projectDirectory,
        Gav rootBom,
        Collection<Gavtcs> rootArtifacts,
        Collection<Gav> additionalBoms) {
}
