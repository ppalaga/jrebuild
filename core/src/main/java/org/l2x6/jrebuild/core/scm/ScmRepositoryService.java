/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild
 *                                 project contributors as indicated by the @author tags
 *                                 SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.scm;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.maven.model.Model;
import org.jboss.logging.Logger;
import org.l2x6.jrebuild.api.scm.FqScmRef.AnnotatedFqScmRef;
import org.l2x6.jrebuild.api.scm.RemoteScmLookup;
import org.l2x6.jrebuild.api.scm.ScmLocator;
import org.l2x6.jrebuild.api.scm.ScmRepository.AnnotatedScmRepository;
import org.l2x6.jrebuild.api.util.Ebnfizer;
import org.l2x6.jrebuild.api.util.IndexedCollection;
import org.l2x6.jrebuild.api.util.JrebuildUtils;
import org.l2x6.jrebuild.core.build.BuildGroup;
import org.l2x6.jrebuild.core.dep.ResolvedArtifactNode;
import org.l2x6.jrebuild.core.scm.ScmRepositoryService.ScmInfoNode.Builder;
import org.l2x6.jrebuild.core.tree.Node;
import org.l2x6.jrebuild.core.tree.Visitor;
import org.l2x6.jrebuild.domino.scm.DominoBuildRecipesScmLocator;
import org.l2x6.jrebuild.pnc.PncScmLocator;
import org.l2x6.jrebuild.reproducible.central.ReproducibleCentralScmLocator;
import org.l2x6.pom.tuner.model.Gav;
import org.l2x6.pom.tuner.model.Gavtc;

public class ScmRepositoryService {
    private static final Logger log = Logger.getLogger(ScmRepositoryService.class);
    private final Map<Gav, AnnotatedFqScmRef> cachedScmInfos = new ConcurrentHashMap<>();
    private final List<ScmLocator> scmLocators;

    public static ScmRepositoryService create(
            Function<Gav, Model> getEffectiveModel,
            RemoteScmLookup remoteScm,
            Path cloneDirectory,
            Path cacheDir,
            Instant maxPncBuildDate,
            Collection<String> reproducibleCentralGitRepositories,
            Collection<String> dominoRecipeUrls,
            String pncBaseUri,
            boolean pncIncludeTemporary) {
        List<ScmLocator> locators = Stream.of(
                new ReproducibleCentralScmLocator(cloneDirectory, cacheDir, reproducibleCentralGitRepositories, remoteScm),
                new DominoBuildRecipesScmLocator(cloneDirectory, dominoRecipeUrls, remoteScm),
                new PomScmLocator(getEffectiveModel, remoteScm),
                pncBaseUri == null ? null
                        : new PncScmLocator(cacheDir, maxPncBuildDate, pncBaseUri, pncIncludeTemporary, remoteScm))
                .filter(loc -> loc != null)
                .map(loc -> (ScmLocator) loc)
                .toList();
        return new ScmRepositoryService(locators);
    }

    ScmRepositoryService(List<ScmLocator> scmLocators) {
        super();
        this.scmLocators = scmLocators;
    }

    @SuppressWarnings("unused")
    public AnnotatedFqScmRef locate(Gav gav, Deque<Builder> stack) {
        return cachedScmInfos.computeIfAbsent(gav, k -> {
            final List<AnnotatedFqScmRef> failures = new ArrayList<>();
            for (ScmLocator scmLocator : scmLocators) {
                for (AnnotatedFqScmRef scmRef : scmLocator.locate(gav)) {
                    if (scmRef.isFailed()) {
                        failures.add(scmRef);
                    } else if (scmRef.isUnknown()) {
                        throw new IllegalStateException(
                                scmLocator.getClass().getName() + ".locate(Gav) should not return an unknown FqScmRef");
                    } else {
                        return scmRef;
                    }
                }
            }
            if (!failures.isEmpty()) {
                final String shortMessage = new Ebnfizer()
                        .add(failures.stream().map(AnnotatedFqScmRef::failureMessage))
                        .toString();
                final StringBuilder failureMessages = new StringBuilder(shortMessage);
                final Iterator<Builder> it = stack.iterator();
                if (it.hasNext()) {
                    failureMessages.append("\n    referenced from ").append(it.next());
                    Builder last = null;
                    while (it.hasNext()) {
                        Builder current = it.next();
                        if (last == null || !current.equals(last)) {
                            /* Eliminate dups on the stack */
                            failureMessages.append("\n    referenced from ").append(current);
                        }
                        last = current;
                    }
                } else {
                    failureMessages.append("\n    <empty context>");
                }
                final String msg = failureMessages.toString();
                log.warn(msg);
                return AnnotatedFqScmRef.createFailed(
                        gav.getVersion(),
                        AnnotatedScmRepository
                                .createFailed(failures.stream().map(AnnotatedFqScmRef::repository).toList()),
                        shortMessage);
            }
            return AnnotatedFqScmRef.createUnknown(gav);
        });
    }

    public ScmRepositoryLocatorVisitor newVisitor() {
        return new ScmRepositoryLocatorVisitor(this::locate);
    }

    public static class ScmRepositoryLocatorVisitor implements Visitor<ResolvedArtifactNode, ScmRepositoryLocatorVisitor> {

        private final BiFunction<Gav, Deque<ScmInfoNode.Builder>, AnnotatedFqScmRef> locate;
        private final Deque<ScmInfoNode.Builder> stack = new ArrayDeque<>();
        private ScmInfoNode.Builder rootNode;

        public ScmRepositoryLocatorVisitor(BiFunction<Gav, Deque<Builder>, AnnotatedFqScmRef> locate) {
            super();
            this.locate = locate;
        }

        @Override
        public boolean enter(ResolvedArtifactNode node) {
            Gavtc gavtc = node.gavtc();
            Gav gav = gavtc.toGav();
            AnnotatedFqScmRef scmRef = locate.apply(gav, stack);
            if (stack.isEmpty()) {
                ScmInfoNode.Builder newNode = ScmInfoNode.builder(BuildGroup.builder(scmRef).artifact(gavtc));
                stack.push(newNode);
            } else {
                ScmInfoNode.Builder parent = stack.peek();
                if (parent.buildGroup.scmRef().equals(scmRef)) {
                    parent.buildGroup.artifact(gavtc);
                    stack.push(parent);
                } else {
                    ScmInfoNode.Builder newNode = parent.getOrAddChildBuilder(scmRef);
                    newNode.buildGroup.artifact(gavtc);
                    stack.push(newNode);
                }
            }
            return true;
        }

        @Override
        public boolean leave(ResolvedArtifactNode node) {
            ScmInfoNode.Builder rn = stack.peek();
            stack.pop();
            if (stack.isEmpty()) {
                this.rootNode = rn;
            }
            return true;
        }

        public ScmInfoNode rootNode() {
            return rootNode.build();
        }
    }

    public static class ScmInfoNode implements Node<ScmInfoNode> {
        private final BuildGroup<AnnotatedFqScmRef> buildGroup;
        private final Set<ScmInfoNode> children;
        private final int hashCode;

        private ScmInfoNode(BuildGroup<AnnotatedFqScmRef> buildGroup, Set<ScmInfoNode> children) {
            super();
            this.buildGroup = Objects.requireNonNull(buildGroup);
            this.children = JrebuildUtils.assertImmutable(Objects.requireNonNull(children));
            this.hashCode = 31 * buildGroup.hashCode() + children.hashCode();
        }

        public static Builder builder(BuildGroup.Builder<AnnotatedFqScmRef> buildGroup) {
            return new Builder(buildGroup);
        }

        public Builder builder() {
            Builder result = new Builder(buildGroup.builder());
            for (ScmInfoNode ch : children) {
                result.children.add(ch.builder());
            }
            return result;
        }

        public BuildGroup<AnnotatedFqScmRef> buildGroup() {
            return buildGroup;
        }

        public Set<ScmInfoNode> children() {
            return children;
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
            ScmInfoNode other = (ScmInfoNode) obj;
            return buildGroup.equals(other.buildGroup) && children.equals(other.children);
        }

        @Override
        public String toString() {
            return buildGroup.toString();
        }

        public static class Builder implements Node<Builder> {
            private final BuildGroup.Builder<AnnotatedFqScmRef> buildGroup;
            private IndexedCollection<AnnotatedFqScmRef, Builder> children = IndexedCollection.linked(
                    b -> b.buildGroup.scmRef(),
                    (Builder b1, Builder b2) -> b1.merge(b2));

            public Builder(BuildGroup.Builder<AnnotatedFqScmRef> buildGroup) {
                this.buildGroup = Objects.requireNonNull(buildGroup);
            }

            @SuppressWarnings("unused")
            public Builder getOrAddChildBuilder(AnnotatedFqScmRef scmRef) {
                return children.computeIfAbsent(scmRef, k -> new Builder(BuildGroup.builder(scmRef)));
            }

            public ScmInfoNode build() {
                final Set<ScmInfoNode> set = children.stream()
                        .map(Builder::build)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
                return new ScmInfoNode(
                        buildGroup.build(),
                        Collections.unmodifiableSet(set));
            }

            @Override
            public Builder merge(Builder other) {
                buildGroup.artifacts(other.buildGroup.artifacts());
                return this;
            }

            @Override
            public int hashCode() {
                return buildGroup.hashCode();
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
                return this.buildGroup.equals(other.buildGroup);
            }

            @Override
            public String toString() {
                return buildGroup.toString();
            }

            @Override
            public Collection<Builder> children() {
                return children;
            }

        }
    }

}
