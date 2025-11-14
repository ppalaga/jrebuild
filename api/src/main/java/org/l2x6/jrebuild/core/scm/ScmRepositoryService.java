/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.scm;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.apache.maven.model.Model;
import org.l2x6.jrebuild.core.build.BuildGroup;
import org.l2x6.jrebuild.core.dep.ResolvedArtifactNode;
import org.l2x6.jrebuild.core.scm.locator.PomScmLocator;
import org.l2x6.jrebuild.core.tree.Node;
import org.l2x6.jrebuild.core.tree.Visitor;
import org.l2x6.pom.tuner.model.Gav;
import org.l2x6.pom.tuner.model.Gavtc;

public class ScmRepositoryService implements ScmLocator {

    private final Map<Gav, ScmRef> cachedScmInfos = new ConcurrentHashMap<>();
    private final List<ScmLocator> scmLocators;

    public static ScmRepositoryService create(
            Function<Gav, Model> getEffectiveModel) {
        return new ScmRepositoryService(List.of(
                new PomScmLocator(getEffectiveModel)));
    }

    ScmRepositoryService(List<ScmLocator> scmLocators) {
        super();
        this.scmLocators = scmLocators;
    }

    @Override
    public ScmRef locate(Gav gav) {
        return cachedScmInfos.computeIfAbsent(gav, k -> {
            ScmRef scmRef;
            for (ScmLocator scmLocator : scmLocators) {
                if ((scmRef = scmLocator.locate(gav)) != null) {
                    return scmRef;
                }
            }
            return null;
        });
    }

    public ScmRepositoryLocatorVisitor newVisitor() {
        return new ScmRepositoryLocatorVisitor();
    }

    public class ScmRepositoryLocatorVisitor implements Visitor<ResolvedArtifactNode, ScmRepositoryLocatorVisitor> {

        private final Deque<ScmInfoNode> stack = new ArrayDeque<>();
        private ScmInfoNode rootNode;

        @Override
        public boolean enter(ResolvedArtifactNode node) {
            Gavtc gavtc = node.gavtc();
            Gav gav = gavtc.toGav();
            ScmRef scmRef = locate(gav);
            if (stack.isEmpty()) {
                ScmInfoNode newNode = new ScmInfoNode(BuildGroup.mutable(scmRef, gavtc));
                stack.push(newNode);
            } else {
                ScmInfoNode parent = stack.peek();
                if (parent.buildGroup.scmRef().equals(scmRef)) {
                    parent.buildGroup.artifacts().add(gavtc);
                    parent.depth.incrementAndGet();
                } else {
                    ScmInfoNode newNode = new ScmInfoNode(BuildGroup.mutable(scmRef, gavtc));
                    parent.children.add(newNode);
                    stack.push(newNode);
                }
            }
            return true;
        }

        @Override
        public boolean leave(ResolvedArtifactNode node) {
            ScmInfoNode rn = stack.peek();
            if (rn.depth.getAndDecrement() == 0) {
                stack.pop();
            }
            if (stack.isEmpty()) {
                this.rootNode = rn;
            }
            return true;
        }

        public ScmInfoNode rootNode() {
            return rootNode;
        }
    }

    public static record ScmInfoNode(
            BuildGroup buildGroup,
            List<ScmInfoNode> children,
            AtomicInteger depth) implements Node<ScmInfoNode> {
        public ScmInfoNode(BuildGroup gavScmInfo) {
            this(gavScmInfo, new ArrayList<>(), new AtomicInteger(0));
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
            return buildGroup.equals(((ScmInfoNode) obj).buildGroup);
        }

        @Override
        public String toString() {
            return buildGroup.toString();
        }

    }
}
