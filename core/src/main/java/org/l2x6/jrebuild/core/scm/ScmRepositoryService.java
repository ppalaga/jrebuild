/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.scm;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.maven.model.Model;
import org.jboss.logging.Logger;
import org.l2x6.jrebuild.api.scm.FqScmRef;
import org.l2x6.jrebuild.api.scm.JrebuildUtils;
import org.l2x6.jrebuild.api.scm.RemoteScmLookup;
import org.l2x6.jrebuild.api.scm.ScmLocator;
import org.l2x6.jrebuild.core.build.BuildGroup;
import org.l2x6.jrebuild.core.dep.ResolvedArtifactNode;
import org.l2x6.jrebuild.core.scm.ScmRepositoryService.ScmInfoNode.Builder;
import org.l2x6.jrebuild.core.tree.Node;
import org.l2x6.jrebuild.core.tree.Visitor;
import org.l2x6.jrebuild.domino.scm.DominoBuildRecipesScmLocator;
import org.l2x6.jrebuild.reproducible.central.ReproducibleCentralScmLocator;
import org.l2x6.pom.tuner.model.Gav;
import org.l2x6.pom.tuner.model.Gavtc;

public class ScmRepositoryService {
    private static final Logger log = Logger.getLogger(ScmRepositoryService.class);
    private final Map<Gav, FqScmRef> cachedScmInfos = new ConcurrentHashMap<>();
    private final List<ScmLocator> scmLocators;

    public static ScmRepositoryService create(
            Function<Gav, Model> getEffectiveModel,
            RemoteScmLookup remoteScm,
            Path cloneDirectory,
            Collection<String> reproducibleCentralGitRepositories,
            Collection<String> dominoRecipeUrls) {
        return new ScmRepositoryService(List.of(
                new ReproducibleCentralScmLocator(cloneDirectory, reproducibleCentralGitRepositories, remoteScm),
                new DominoBuildRecipesScmLocator(cloneDirectory, dominoRecipeUrls, remoteScm),
                new PomScmLocator(getEffectiveModel, remoteScm)));
    }

    ScmRepositoryService(List<ScmLocator> scmLocators) {
        super();
        this.scmLocators = scmLocators;
    }

    public FqScmRef locate(Gav gav, Deque<Builder> stack) {
        return cachedScmInfos.computeIfAbsent(gav, k -> {
            FqScmRef scmRef;
            StringBuilder failureMessages = null;
            String failureMessagePrefix = null;
            for (ScmLocator scmLocator : scmLocators) {
                try {
                    scmRef = scmLocator.locate(gav);
                    if (scmRef != null) {
                        return scmRef;
                    }
                } catch (Exception e) {
                    if (failureMessages == null) {
                        failureMessages = new StringBuilder("Failed to locate SCM ref for ")
                                .append(gav)
                                .append(":");
                        final Iterator<Builder> it = stack.descendingIterator();
                        if (it.hasNext()) {
                            failureMessages.append("\n    -> ").append(it.next());
                            Builder last = null;
                            while (it.hasNext()) {
                                Builder current = it.next();
                                if (last == null || !current.equals(last)) {
                                    /* Eliminate dups on the stack */
                                    failureMessages.append("\n    -> ").append(current);
                                }
                                last = current;
                            }
                        } else {
                            failureMessages.append("\n    <empty context>");
                        }
                        failureMessages.append('\n');
                        failureMessagePrefix = failureMessages.toString();
                    }
                    final StringWriter sw = new StringWriter();
                    try (PrintWriter pw = new PrintWriter(sw)) {
                        e.printStackTrace(pw);
                    }
                    failureMessages.append('\n').append(sw.toString());
                    log.warn(failureMessagePrefix, e);
                }
            }
            if (failureMessages != null) {
                return FqScmRef.createFailed(gav, failureMessages.toString());
            }
            return FqScmRef.createUnknown(gav);
        });
    }

    public ScmRepositoryLocatorVisitor newVisitor() {
        return new ScmRepositoryLocatorVisitor(this::locate);
    }

    public static class ScmRepositoryLocatorVisitor implements Visitor<ResolvedArtifactNode, ScmRepositoryLocatorVisitor> {

        private final BiFunction<Gav, Deque<ScmInfoNode.Builder>, FqScmRef> locate;
        private final Deque<ScmInfoNode.Builder> stack = new ArrayDeque<>();
        private ScmInfoNode.Builder rootNode;

        public ScmRepositoryLocatorVisitor(BiFunction<Gav, Deque<Builder>, FqScmRef> locate) {
            super();
            this.locate = locate;
        }

        @Override
        public boolean enter(ResolvedArtifactNode node) {
            Gavtc gavtc = node.gavtc();
            Gav gav = gavtc.toGav();
            FqScmRef scmRef = locate.apply(gav, stack);
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
        private final BuildGroup buildGroup;
        private final Set<ScmInfoNode> children;
        private final int hashCode;

        private ScmInfoNode(BuildGroup buildGroup, Set<ScmInfoNode> children) {
            super();
            this.buildGroup = Objects.requireNonNull(buildGroup);
            this.children = JrebuildUtils.assertImmutable(Objects.requireNonNull(children));
            this.hashCode = 31 * buildGroup.hashCode() + children.hashCode();
        }

        public static Builder builder(BuildGroup.Builder gavScmInfo) {
            return new Builder(gavScmInfo);
        }

        public BuildGroup buildGroup() {
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

        public static class Builder {
            private final BuildGroup.Builder buildGroup;
            private Map<FqScmRef, Builder> children = new LinkedHashMap<>();

            public Builder(BuildGroup.Builder buildGroup) {
                this.buildGroup = Objects.requireNonNull(buildGroup);
            }

            public Builder getOrAddChildBuilder(FqScmRef scmRef) {
                return children.computeIfAbsent(scmRef, k -> new Builder(BuildGroup.builder(scmRef)));
            }

            public ScmInfoNode build() {
                final Set<ScmInfoNode> set = children.values().stream()
                        .map(Builder::build)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
                return new ScmInfoNode(
                        buildGroup.build(),
                        Collections.unmodifiableSet(set));
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

        }
    }

}
