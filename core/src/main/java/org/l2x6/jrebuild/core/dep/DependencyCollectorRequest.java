/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.dep;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.l2x6.pom.tuner.model.Gav;
import org.l2x6.pom.tuner.model.Gavtc;

public record DependencyCollectorRequest(
        /** The root directory of a Maven source tree if we are analyzing a source tree; otherwise {@code null} */
        Path projectDirectory,
        Gav rootBom,
        Collection<Gavtc> rootArtifacts,
        Collection<Gav> additionalBoms,
        boolean includeOptionalDependencies) {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Path projectDirectory;
        private Gav rootBom;
        private List<Gavtc> rootArtifacts = new ArrayList<>();
        private List<Gav> additionalBoms = new ArrayList<>();
        private boolean includeOptionalDependencies = false;

        public Builder projectDirectory(Path projectDirectory) {
            this.projectDirectory = projectDirectory;
            return this;
        }

        public Builder rootBom(Gav rootBom) {
            this.rootBom = rootBom;
            return this;
        }

        public Builder rootArtifacts(Collection<Gavtc> rootArtifacts) {
            this.rootArtifacts.addAll(rootArtifacts);
            return this;
        }

        public Builder rootArtifacts(Gavtc... rootArtifacts) {
            for (Gavtc gavtcs : rootArtifacts) {
                this.rootArtifacts.add(gavtcs);
            }
            return this;
        }

        public Builder includeOptionalDependencies(boolean includeOptionalDependencies) {
            this.includeOptionalDependencies = includeOptionalDependencies;
            return this;
        }

        public Builder includeOptionalDependencies() {
            this.includeOptionalDependencies = true;
            return this;
        }

        public Builder additionalBoms(Collection<Gav> additionalBoms) {
            this.additionalBoms.addAll(additionalBoms);
            return this;
        }

        public DependencyCollectorRequest build() {
            Collection<Gavtc> rArtifs = Collections.unmodifiableList(rootArtifacts);
            rootArtifacts = null;
            Collection<Gav> aBoms = Collections.unmodifiableList(additionalBoms);
            additionalBoms = null;
            return new DependencyCollectorRequest(projectDirectory, rootBom, rArtifs, aBoms, includeOptionalDependencies);
        }
    }
}
