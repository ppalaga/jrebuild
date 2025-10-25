/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.dep;

import org.eclipse.aether.graph.DependencyNode;
import org.l2x6.pom.tuner.model.Gavtcs;

public record ArtifactDependencyTree(
        Gavtcs rootGavtcs,
        DependencyNode rootNode) implements Comparable<ArtifactDependencyTree> {

    @Override
    public int compareTo(ArtifactDependencyTree o) {
        return Gavtcs.groupFirstComparator().compare(rootGavtcs, o.rootGavtcs);
    }
}
