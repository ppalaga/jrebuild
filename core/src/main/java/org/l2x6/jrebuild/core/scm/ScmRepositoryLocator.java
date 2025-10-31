/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.scm;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.apache.commons.text.StringSubstitutor;
import org.apache.maven.model.Model;
import org.apache.maven.model.Scm;
import org.l2x6.jrebuild.core.dep.ResolvedArtifact;
import org.l2x6.jrebuild.core.tree.Node;
import org.l2x6.jrebuild.core.tree.Visitor;
import org.l2x6.pom.tuner.model.Gav;
import org.l2x6.pom.tuner.model.Gavtc;

public class ScmRepositoryLocator {

    private final Map<Gav, ScmRef> cachedScmInfos = new ConcurrentHashMap<>();
    private final Function<Gav, Model> getEffectiveModel;

    public ScmRepositoryLocator(Function<Gav, Model> getEffectiveModel) {
        super();
        this.getEffectiveModel = getEffectiveModel;
    }

    public ScmRepositoryLocatorVisitor newVisitor() {
        return new ScmRepositoryLocatorVisitor();
    }

    public class ScmRepositoryLocatorVisitor implements Visitor<ResolvedArtifact, ScmRepositoryLocatorVisitor> {

        private final Deque<ScmInfoNode> stack = new ArrayDeque<>();
        private ScmInfoNode rootNode;

        @Override
        public boolean enter(ResolvedArtifact node) {
            Gavtc gavtc = node.gavtc();
            Gav gav = gavtc.toGav();
            if (stack.isEmpty()) {
                ScmRef scmRef = cachedScmInfos.computeIfAbsent(gav, k -> findScmRef(gav, node));
                ScmInfoNode newNode = new ScmInfoNode(BuildGroup.mutable(scmRef, gavtc));
                stack.push(newNode);
            } else {
                ScmInfoNode parent = stack.peek();
                ScmRef scmRef = cachedScmInfos.computeIfAbsent(gav, k -> findScmRef(gav, node));
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
        public boolean leave(ResolvedArtifact node) {
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

        private ScmRef findScmRef(Gav gav, ResolvedArtifact node) {
            final Model model = getEffectiveModel.apply(gav);
            return findScmRef(gav, model, gav.getVersion());
        }

        /**
         * Adapted from io.quarkus.domino.recipes.scm.AbstractPomScmLocator
         *
         * @param  gav
         * @param  model
         * @param  version
         * @return
         */
        static ScmRef findScmRef(Gav gav, Model model, String version) {
            final Scm scm = model.getScm();
            if (scm == null) {
                return null;
            }
            if (scm.getConnection() != null && !scm.getConnection().isEmpty()) {
                return toScmRef(gav, scm, resolveModelValue(model, scm.getConnection(), version));
            }
            String url = resolveModelValue(model, model.getUrl(), version);
            if (url != null && url.startsWith("https://github.com/")) {
                return toScmRef(gav, scm, url);
            }
            url = resolveModelValue(model, scm.getUrl(), version);
            if (url != null && url.startsWith("https://github.com/")) {
                return toScmRef(gav, scm, url);
            }
            return null;
        }

        private static ScmRef toScmRef(Gav gav, final Scm scm, String url) {
            return ScmRef.of(scm.getTag(), new ScmRepository("git", normalizeScmUri(url)));
        }

        private static final String HTTPS_GITHUB_COM = "https://github.com/";

        private static String normalizeScmUri(String s) {
            s = s.replace("scm:", "");
            s = s.replace("git:", "");
            s = s.replace("git@", "");
            s = s.replace("ssh:", "");
            s = s.replace("svn:", "");
            // s = s.replace(".git", "");
            if (s.startsWith("http://")) {
                s = s.replace("http://", "https://");
            } else if (!s.startsWith("https://")) {
                s = s.replace(':', '/');
                if (s.startsWith("github.com:")) {
                    s = s.replace(':', '/');
                }
                if (s.startsWith("//")) {
                    s = "https:" + s;
                } else {
                    s = "https://" + s;
                }
            }
            if (s.startsWith(HTTPS_GITHUB_COM)) {
                var tmp = s.substring(HTTPS_GITHUB_COM.length());
                final String[] parts = tmp.split("/");
                if (parts.length > 2) {
                    s = HTTPS_GITHUB_COM + parts[0] + "/" + parts[1];
                }
            }
            return s;
        }

        private static String resolveModelValue(Model model, String value, String version) {
            return value == null ? null : value.contains("${") ? substituteProperties(value, model, version) : value;
        }

        private static String substituteProperties(String str, Model model, String version) {
            final Properties props = model.getProperties();
            Map<String, String> map = new HashMap<>(props.size());
            for (Map.Entry<?, ?> prop : props.entrySet()) {
                map.put(prop.getKey().toString(), prop.getValue().toString());
            }
            map.put("project.version", version);
            map.put("project.artifactId", model.getArtifactId());
            return new StringSubstitutor(map).replace(str);
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
