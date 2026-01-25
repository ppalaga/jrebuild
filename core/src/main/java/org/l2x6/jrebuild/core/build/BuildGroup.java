/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.build;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.l2x6.jrebuild.api.scm.FqScmRef;
import org.l2x6.pom.tuner.model.Gav;
import org.l2x6.pom.tuner.model.Gavtc;

public class BuildGroup {

    protected final FqScmRef scmRef;
    protected final Set<Gavtc> artifacts;

    public BuildGroup(FqScmRef scmRef, Set<Gavtc> artifacts) {
        super();
        this.scmRef = scmRef;
        this.artifacts = artifacts;
    }

    public FqScmRef scmRef() {
        return scmRef;
    }

    public Set<Gavtc> artifacts() {
        return artifacts;
    }

    public static Builder builder(FqScmRef scmRef) {
        return new Builder(scmRef);
    }

    @Override
    public int hashCode() {
        return scmRef.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        BuildGroup other = (BuildGroup) obj;
        return Objects.equals(scmRef, other.scmRef);
    }

    public boolean contains(Gav gav) {
        return artifacts.stream()
                .map(Gavtc::toGav)
                .filter(gav::equals)
                .findAny().isPresent();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(scmRef.isUnknown() ? "❌ " : "✅ ");
        sb.append(scmRef);
        if (artifacts.isEmpty()) {
            sb.append(" []");
        } else if (artifacts.size() == 1) {
            sb.append(" [").append(artifacts.iterator().next()).append("]");
        } else {
            sb.append(" [");
            sb.append(artifacts.stream().map(a -> a.getGroupId() + ":*:" + a.getVersion()).distinct()
                    .collect(Collectors.joining(", ")));
            sb.append("]");
        }
        return sb.toString();
    }

    public static class Builder extends BuildGroup {

        public Builder(FqScmRef scmRef) {
            super(scmRef, new TreeSet<>(Gavtc.groupFirstComparator()));
        }

        public Builder artifact(Gavtc artifact) {
            this.artifacts.add(artifact);
            return this;
        }

        public Builder merge(BuildGroup other) {
            if (!this.scmRef.equals(other.scmRef)) {
                throw new IllegalStateException("Cannot merge BuildGroup with scmRef "+ other.scmRef + " into BuildGroup with scmRef "+ this.scmRef + "; they must be equal");
            }
            this.artifacts.addAll(other.artifacts);
            return this;
        }

        public BuildGroup build() {
            TreeSet<Gavtc> arts = new TreeSet<>(Gavtc.groupFirstComparator());
            return new BuildGroup(scmRef, Collections.unmodifiableSet(arts));
        }

    }

}
