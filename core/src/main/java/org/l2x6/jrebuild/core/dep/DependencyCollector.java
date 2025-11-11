/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.dep;

import eu.maveniverse.maven.mima.context.Context;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;
import org.eclipse.aether.util.graph.selector.AndDependencySelector;
import org.eclipse.aether.util.graph.selector.ExclusionDependencySelector;
import org.eclipse.aether.util.graph.selector.ScopeDependencySelector;
import org.l2x6.jrebuild.core.mima.internal.CachingMavenModelReader;
import org.l2x6.jrebuild.core.mima.internal.CachingMavenModelReader.ModelData;
import org.l2x6.pom.tuner.model.Gav;
import org.l2x6.pom.tuner.model.Gavtc;

public class DependencyCollector {

    public static Stream<ResolvedArtifactNode> collect(Context context, DependencyCollectorRequest request) {

        return request.rootArtifacts().parallelStream()
                .map(rootGavtc -> {

                    final Artifact rootArtifact = JrebuildUtils.toAetherArtifact(rootGavtc);

                    org.eclipse.aether.graph.Dependency dependency = new org.eclipse.aether.graph.Dependency(rootArtifact,
                            "runtime");
                    CollectRequest collectRequest = new CollectRequest(dependency, context.remoteRepositories());

                    RepositorySystemSession repoSession = context.repositorySystemSession();
                    if (request.includeOptionalDependencies()) {
                        repoSession = new DefaultRepositorySystemSession(repoSession);
                        ((DefaultRepositorySystemSession) repoSession).setDependencySelector(new AndDependencySelector(
                                new ScopeDependencySelector("test", "provided"),
                                new ExclusionDependencySelector()));
                    }

                    try {
                        final DependencyNode rootNode = context.repositorySystem()
                                .collectDependencies(repoSession, collectRequest)
                                .getRoot();
                        ResolvedArtifactNodeVisitor v = new ResolvedArtifactNodeVisitor(
                                request.includeParentsAndImports()
                                        ? new ParentsAndImportsResolver(
                                                context.lookup().lookup(CachingMavenModelReader.class).get())
                                        : (a, r) -> {
                                        });
                        rootNode.accept(v);
                        return v.rootNode;
                    } catch (DependencyCollectionException e) {
                        throw new RuntimeException("Could not resolve " + rootGavtc);
                    }
                });
    }

    static class ParentsAndImportsResolver implements BiConsumer<Artifact, Consumer<ResolvedArtifactNode>> {
        private final CachingMavenModelReader modelReader;

        public ParentsAndImportsResolver(CachingMavenModelReader modelReader) {
            super();
            this.modelReader = modelReader;
        }

        @Override
        public void accept(Artifact a, Consumer<ResolvedArtifactNode> result) {
            ModelData resp = modelReader.readModel(a);

            /* Predecessors axis */
            if (resp.parent() != null) {
                /* Ignore the super pom */
                Gavtc gav = resp.parent().toGavtc("pom", null);
                List<ResolvedArtifactNode> children = new ArrayList<>();
                traverse(resp.parent(), children::add);
                result.accept(new ResolvedArtifactNode(gav, Collections.unmodifiableList(children)));
            }

            /* Imports axis */
            traverseImports(resp.interpolatedModel(), result);
        }

        void traverseImports(Model interpolatedModel, Consumer<ResolvedArtifactNode> result) {
            final DependencyManagement dm = interpolatedModel.getDependencyManagement();
            final List<Dependency> deps;
            if (dm != null && (deps = dm.getDependencies()) != null && !deps.isEmpty()) {
                for (Dependency dep : deps) {
                    if ("import".equals(dep.getScope())) {
                        Gavtc gav = new Gavtc(dep.getGroupId(), dep.getArtifactId(), dep.getVersion(), dep.getType(), null);
                        if (!"pom".equals(dep.getType())) {
                            throw new IllegalStateException("Unexpected dependency management import type '" + dep.getType()
                                    + "' of " + gav + " in " + interpolatedModel.getId());
                        }
                        List<ResolvedArtifactNode> children = new ArrayList<>();
                        accept(JrebuildUtils.toAetherArtifact(gav), children::add);
                        result.accept(new ResolvedArtifactNode(gav, Collections.unmodifiableList(children)));
                    }
                }
            }
        }

        void traverse(Gav a, Consumer<ResolvedArtifactNode> result) {
            ModelData resp = modelReader.readModel(a);

            /* Predecessors axis */
            if (resp.parent() != null) {
                /* Ignore the super pom */
                Gavtc gav = resp.parent().toGavtc("pom", null);
                List<ResolvedArtifactNode> children = new ArrayList<>();
                traverse(resp.parent(), children::add);
                result.accept(new ResolvedArtifactNode(gav, Collections.unmodifiableList(children)));
            }

            /* Imports axis */
            traverseImports(resp.interpolatedModel(), result);
        }
    }

    static class ResolvedArtifactNodeVisitor implements DependencyVisitor {

        private final Deque<ResolvedArtifactNode> stack = new ArrayDeque<>();
        private ResolvedArtifactNode rootNode;
        private final BiConsumer<Artifact, Consumer<ResolvedArtifactNode>> parentsAndImportsResolver;

        public ResolvedArtifactNodeVisitor(BiConsumer<Artifact, Consumer<ResolvedArtifactNode>> parentsAndImportsResolver) {
            super();
            this.parentsAndImportsResolver = parentsAndImportsResolver;
        }

        @Override
        public boolean visitEnter(DependencyNode node) {
            final Artifact artifact = node.getArtifact();
            final Gavtc gavtc = JrebuildUtils.toGavtc(artifact);
            final ArrayList<ResolvedArtifactNode> children = new ArrayList<>();
            parentsAndImportsResolver.accept(artifact, children::add);
            final ResolvedArtifactNode newNode = new ResolvedArtifactNode(gavtc, children);
            final ResolvedArtifactNode parent = stack.peek();
            if (parent != null) {
                parent.children().add(newNode);
            }
            stack.push(newNode);
            return true;
        }

        @Override
        public boolean visitLeave(DependencyNode node) {
            ResolvedArtifactNode rn = stack.pop();
            rn.makeImmutable();
            if (stack.isEmpty()) {
                this.rootNode = rn;
            }
            return true;
        }

    }

}
