/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.dep;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.eclipse.aether.repository.RemoteRepository;
import org.l2x6.jrebuild.api.scm.JrebuildUtils;
import org.l2x6.jrebuild.core.scm.ScmRepositoryService.ScmInfoNode.Builder;
import org.l2x6.jrebuild.core.tree.Node;
import org.l2x6.pom.tuner.model.Gavtc;

public class ResolvedArtifactNode implements Comparable<ResolvedArtifactNode>, Node<ResolvedArtifactNode> {
    private final DependencyAxis axis;
    private final Gavtc gavtc;
    private List<ResolvedArtifactNode> children;
    private final List<RemoteRepository> repositories;
    private final int hashCode;

    private ResolvedArtifactNode(DependencyAxis axis, Gavtc rootGavtc, List<ResolvedArtifactNode> children,
            List<RemoteRepository> repositories) {
        super();
        this.axis = axis;
        this.gavtc = rootGavtc;
        this.children = JrebuildUtils.assertImmutable(children);
        this.repositories = JrebuildUtils.assertImmutable(repositories);
        this.hashCode = 31 * (31 * gavtc.hashCode() + children.hashCode()) + repositories.hashCode();
    }

    public Gavtc gavtc() {
        return gavtc;
    }

    @Override
    public List<ResolvedArtifactNode> children() {
        return children;
    }

    public List<RemoteRepository> repositories() {
        return repositories;
    }

    @Override
    public int compareTo(ResolvedArtifactNode o) {
        return Gavtc.groupFirstComparator().compare(gavtc, o.gavtc);
    }

    public String toString() {
        return axis + " " + gavtc.toString();
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ResolvedArtifactNode other = (ResolvedArtifactNode) obj;
        return gavtc.equals(other.gavtc) && children.equals(other.children) && repositories.equals(other.repositories);
    }

    StringBuilder append(StringBuilder sb) {
        return append(sb, axis, gavtc, repositories);
    }

    static StringBuilder append(StringBuilder sb, DependencyAxis axis, Gavtc gavtc, List<RemoteRepository> repositories) {
        sb.append("\n    -> ").append(axis).append(gavtc);
        for (RemoteRepository repo : repositories) {
            final String url = repo.getUrl();
            sb.append("\n        ").append(url);
            if (!url.endsWith("/")) {
                sb.append('/');
            }
            sb.append(gavtc.getGroupId().replace('.', '/'))
                    .append('/').append(gavtc.getArtifactId())
                    .append('/').append(gavtc.getVersion())
                    .append('/').append(gavtc.getArtifactId()).append('-').append(gavtc.getVersion()).append(".pom");
        }
        return sb;
    }

    public static enum DependencyAxis {
        DEPENDENCY("🟢"),
        IMPORT("⛴"),
        PARENT("👴");

        private final String symbol;

        private DependencyAxis(String symbol) {
            this.symbol = symbol;
        }

        public String toString() {
            return symbol;
        }
    }

    public static class Builder {
        private final DependencyAxis axis;
        private final Gavtc gavtc;
        private final List<Builder> children = new ArrayList<>();
        private final List<RemoteRepository> repositories;

        public Builder(DependencyAxis axis, Gavtc gavtc, List<RemoteRepository> repositories) {
            super();
            this.axis = axis;
            this.gavtc = Objects.requireNonNull(gavtc);
            this.repositories = JrebuildUtils.assertImmutable(Objects.requireNonNull(repositories));
        }

        public Builder child(Builder child) {
            children.add(child);
            return this;
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
            Builder other = (Builder) obj;
            return gavtc.equals(other.gavtc);
        }

        public ResolvedArtifactNode build() {
            return new ResolvedArtifactNode(
                    axis,
                    gavtc,
                    Collections.unmodifiableList(
                            children.stream()
                                    .map(Builder::build)
                                    .toList()),
                    repositories);
        }

        StringBuilder append(StringBuilder sb) {
            return ResolvedArtifactNode.append(sb, axis, gavtc, repositories);
        }

    }

}
