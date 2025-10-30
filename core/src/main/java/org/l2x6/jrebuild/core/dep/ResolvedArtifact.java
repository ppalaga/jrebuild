/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.dep;

import java.util.List;
import java.util.stream.Collectors;
import org.eclipse.aether.graph.DependencyNode;
import org.l2x6.jrebuild.core.tree.Node;
import org.l2x6.pom.tuner.model.Gavtc;

public class ResolvedArtifact implements Comparable<ResolvedArtifact>, Node<ResolvedArtifact> {
    private final Gavtc gavtc;
    private final DependencyNode rootNode;

    private volatile List<ResolvedArtifact> children;

    public ResolvedArtifact(Gavtc rootGavtc, DependencyNode rootNode) {
        super();
        this.gavtc = rootGavtc;
        this.rootNode = rootNode;
    }

    public Gavtc gavtc() {
        return gavtc;
    }

    @Override
    public List<ResolvedArtifact> children() {
        List<ResolvedArtifact> deps;
        if ((deps = this.children) == null) {
            /* we intentionally do not lock here because
             * because creating the list multiple times should
             * not happen all too often and the result should be the same */
            deps = this.children = rootNode.getChildren().stream()
                    .map(ch -> new ResolvedArtifact(JrebuildUtils.toGavtc(ch.getArtifact()), ch))
                    .collect(Collectors.toUnmodifiableList());
        }
        return deps;
    }

    @Override
    public int compareTo(ResolvedArtifact o) {
        return Gavtc.groupFirstComparator().compare(gavtc, o.gavtc);
    }

    public String toString() {
        return gavtc.toString();
    }

    @Override
    public int hashCode() {
        return gavtc.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ResolvedArtifact other = (ResolvedArtifact) obj;
        return gavtc.equals(other.gavtc);
    }

}
