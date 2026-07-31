/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild
 *                                 project contributors as indicated by the @author tags
 *                                 SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.dep;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.l2x6.pom.tuner.model.Gav;
import org.l2x6.pom.tuner.model.Gavtc;
import org.l2x6.pom.tuner.model.GavtcsPattern;

public record DependencyCollectorRequest(
        /** The root directory of a Maven source tree if we are analyzing a source tree; otherwise {@code null} */
        Path projectDirectory,
        Gav rootBom,
        Collection<Gavtc> rootArtifacts,
        Collection<GavtcsPattern> excludes,
        Collection<Gav> additionalBoms,
        boolean includeOptionalDependencies,
        boolean includeParentsAndImports) {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Path projectDirectory;
        private Gav rootBom;
        private List<Gavtc> rootArtifacts = new ArrayList<>();
        private List<GavtcsPattern> excludes = new ArrayList<>();
        private List<Gav> additionalBoms = new ArrayList<>();
        private boolean includeOptionalDependencies = false;
        private boolean includeParentsAndImports = true;

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

        public Builder includeParentsAndImports(boolean includeParentsAndImports) {
            this.includeParentsAndImports = includeParentsAndImports;
            return this;
        }

        public Builder includeParentsAndImports() {
            this.includeParentsAndImports = true;
            return this;
        }

        public Builder additionalBoms(Gav... additionalBoms) {
            for (Gav additionalBom : additionalBoms) {
                this.additionalBoms.add(additionalBom);
            }
            return this;
        }

        public Builder additionalBoms(Collection<Gav> additionalBoms) {
            this.additionalBoms.addAll(additionalBoms);
            return this;
        }

        public Builder excludes(GavtcsPattern... excludes) {
            for (GavtcsPattern pattern : excludes) {
                this.excludes.add(pattern);
            }
            return this;
        }

        public Builder excludes(Collection<GavtcsPattern> excludes) {
            this.excludes.addAll(excludes);
            return this;
        }

        public DependencyCollectorRequest build() {
            Collection<Gavtc> rArtifs = Collections.unmodifiableList(rootArtifacts);
            rootArtifacts = null;
            Collection<Gav> aBoms = Collections.unmodifiableList(additionalBoms);
            additionalBoms = null;
            Collection<GavtcsPattern> excls = Collections.unmodifiableList(excludes);
            excludes = null;
            return new DependencyCollectorRequest(
                    projectDirectory,
                    rootBom,
                    rArtifs,
                    excls,
                    aBoms,
                    includeOptionalDependencies,
                    includeParentsAndImports);
        }
    }
}
