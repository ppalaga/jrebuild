/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.build;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import org.l2x6.jrebuild.api.scm.FqScmRef;
import org.l2x6.jrebuild.api.scm.JrebuildUtils;
import org.l2x6.pom.tuner.model.Ga;
import org.l2x6.pom.tuner.model.Gav;
import org.l2x6.pom.tuner.model.Gavtc;

public class BuildGroup {

    private final FqScmRef scmRef;
    private final Set<Gavtc> artifacts;
    private final int hashCode;

    private BuildGroup(FqScmRef scmRef, Set<Gavtc> artifacts) {
        super();
        this.scmRef = Objects.requireNonNull(scmRef);
        this.artifacts = JrebuildUtils.assertImmutable(Objects.requireNonNull(artifacts));
        this.hashCode = 31 * scmRef.hashCode() + artifacts.hashCode();
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

    public Builder builder() {
        return new Builder(scmRef).artifacts(artifacts);
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
        BuildGroup other = (BuildGroup) obj;
        return scmRef.equals(other.scmRef) && artifacts.equals(other.artifacts);
    }

    public boolean contains(Gav gav) {
        return artifacts.stream()
                .map(Gavtc::toGav)
                .filter(gav::equals)
                .findAny().isPresent();
    }

    @Override
    public String toString() {
        return append(new StringBuilder(), scmRef, artifacts).toString();
    }

    public static StringBuilder append(StringBuilder sb, FqScmRef scmRef, Set<Gavtc> artifacts) {
        sb.append(scmRef);
        if (artifacts.isEmpty()) {
            sb.append(" []");
        } else if (artifacts.size() == 1) {
            sb.append(" [").append(artifacts.iterator().next()).append("]");
        } else {
            final Map<Ga, CharNode> artifactIdsByGroupVersion = new TreeMap<>();
            artifacts.stream().forEach(a -> {
                final Ga key = new Ga(a.getGroupId(), a.getVersion());
                artifactIdsByGroupVersion.computeIfAbsent(key, k -> CharNode.root()).add(a.getArtifactId());
            });
            boolean first = true;

            sb.append(" [");
            for (Entry<Ga, CharNode> en : artifactIdsByGroupVersion.entrySet()) {
                Ga ga = en.getKey();
                String groupId = ga.getGroupId();
                String version = ga.getArtifactId();
                if (first) {
                    first = false;
                } else {
                    sb.append(',');
                }
                sb.append(groupId).append(':');
                en.getValue().append(sb);
                sb.append(':').append(version);
            }
            sb.append("]");
        }
        return sb;
    }

    static record CharNode(char ch, Map<Character, CharNode> children) {
        public void add(String chars) {
            add(chars.toCharArray(), 0);
        }

        public void append(StringBuilder sb) {
            if (children.isEmpty()) {
                return;
            } else if (children.size() == 1) {
                CharNode child = children.values().iterator().next();
                sb.append(child.ch);
                child.append(sb);
            } else {
                sb.append('[');
                boolean first = true;
                for (CharNode child : children.values()) {
                    if (first) {
                        first = false;
                    } else {
                        sb.append('|');
                    }
                    sb.append(child.ch);
                    child.append(sb);
                }
                sb.append(']');
            }
        }

        public static CharNode root() {
            return new CharNode((char) 0, new TreeMap<>());
        }

        public void add(char[] chars, int offset) {
            char c = chars[offset++];
            CharNode childNode = children.computeIfAbsent(c, k -> new CharNode(c, new TreeMap<>()));
            if (offset < chars.length) {
                childNode.add(chars, offset);
            }
        }
    }

    public static class Builder {
        private final FqScmRef scmRef;
        private final SortedSet<Gavtc> artifacts;

        public Builder(FqScmRef scmRef) {
            this.scmRef = scmRef;
            this.artifacts = new TreeSet<>(Gavtc.groupFirstComparator());
        }

        public FqScmRef scmRef() {
            return scmRef;
        }

        public Builder artifact(Gavtc artifact) {
            this.artifacts.add(artifact);
            return this;
        }

        public Builder artifacts(Collection<Gavtc> artifacts) {
            this.artifacts.addAll(artifacts);
            return this;
        }

        public Builder merge(BuildGroup other) {
            if (!this.scmRef.equals(other.scmRef)) {
                throw new IllegalStateException("Cannot merge BuildGroup with scmRef " + other.scmRef
                        + " into BuildGroup with scmRef " + this.scmRef + "; they must be equal");
            }
            this.artifacts.addAll(other.artifacts);
            return this;
        }

        public BuildGroup build() {
            return new BuildGroup(scmRef, Collections.unmodifiableSet(new TreeSet<>(this.artifacts)));
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
            Builder other = (Builder) obj;
            return Objects.equals(scmRef, other.scmRef);
        }

        @Override
        public String toString() {
            return BuildGroup.append(new StringBuilder(), scmRef, artifacts).toString();
        }

    }

}
