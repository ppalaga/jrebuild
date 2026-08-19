/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild
 *                                 project contributors as indicated by the @author tags
 *                                 SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.dep;

import eu.maveniverse.maven.mima.context.Context;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionContext;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;
import org.eclipse.aether.graph.Exclusion;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.util.graph.selector.AndDependencySelector;
import org.eclipse.aether.util.graph.selector.ExclusionDependencySelector;
import org.eclipse.aether.util.graph.selector.ScopeDependencySelector;
import org.jboss.logging.Logger;
import org.l2x6.jrebuild.core.dep.ResolvedArtifactNode.DependencyAxis;
import org.l2x6.jrebuild.core.mima.internal.CachingMavenModelReader;
import org.l2x6.jrebuild.core.mima.internal.CachingMavenModelReader.ModelData;
import org.l2x6.pom.tuner.model.Gav;
import org.l2x6.pom.tuner.model.Gavtc;
import org.l2x6.pom.tuner.model.Gavtc.Type;
import org.l2x6.pom.tuner.model.GavtcsPattern;

public class DependencyCollector {
    private static final Logger log = Logger.getLogger(DependencyCollector.class);

    public static Multi<ResolvedArtifactNode> collect(Context context, DependencyCollectorRequest request) {

        final CachingMavenModelReader modelReader = context.lookup().lookup(CachingMavenModelReader.class).get();
        final List<org.eclipse.aether.graph.Dependency> constraints = new ArrayList<>();
        final Gav rootBom = request.rootBom();
        if (rootBom != null) {
            collectConstraints(rootBom, modelReader, constraints::add);
        }
        request.additionalBoms().forEach(additionalBom -> collectConstraints(additionalBom, modelReader, constraints::add));

        if (log.isTraceEnabled()) {
            log.tracef("Dependency constraints:\n    - %s",
                    constraints.stream().map(d -> d.getArtifact().getGroupId() + ":" + d.getArtifact().getArtifactId() + ":"
                            + d.getArtifact().getVersion()).collect(Collectors.joining("\n    - ")));
        }

        RepositorySystemSession rSession = context.repositorySystemSession();
        final Collection<GavtcsPattern> excludes = request.excludes();
        if (request.includeOptionalDependencies() || !excludes.isEmpty()) {
            List<DependencySelector> selectors = new ArrayList<>(3);
            selectors.add(new ScopeDependencySelector("test", "provided"));
            selectors.add(new ExclusionDependencySelector());
            if (request.includeOptionalDependencies()) {
                /* The default instance contains org.eclipse.aether.util.graph.selector.OptionalDependencySelector
                 * which we do not add here */
            }
            if (!excludes.isEmpty()) {
                selectors.add(new ExcludesDependencySelector(excludes));
            }
            rSession = new DefaultRepositorySystemSession(rSession);
            ((DefaultRepositorySystemSession) rSession).setDependencySelector(new AndDependencySelector(selectors));
        }
        final RepositorySystemSession repoSession = rSession;

        return Multi.createFrom().iterable(request.rootArtifacts())
                .onItem().transformToUniAndMerge(rootGavtc -> Uni.createFrom().item(() -> collectDependencies(
                        context,
                        repoSession,
                        modelReader,
                        request,
                        constraints,
                        rootGavtc))
                        .runSubscriptionOn(Infrastructure.getDefaultWorkerPool()));

    }

    static ResolvedArtifactNode collectDependencies(
            Context context,
            RepositorySystemSession repoSession,
            CachingMavenModelReader modelReader,
            DependencyCollectorRequest request,
            List<org.eclipse.aether.graph.Dependency> constraints,
            Gavtc rootGavtc) {
        log.infof("Analyzing dependencies of %s", rootGavtc);

        final Artifact rootArtifact = JrebuildUtils.toAetherArtifact(rootGavtc);

        org.eclipse.aether.graph.Dependency dependency = new org.eclipse.aether.graph.Dependency(rootArtifact,
                "runtime");
        final CollectRequest collectRequest = new CollectRequest(dependency, context.remoteRepositories())
                .setManagedDependencies(constraints);

        CollectResult result = null;
        try {
            result = context.repositorySystem()
                    .collectDependencies(repoSession, collectRequest);
            final DependencyNode rootNode = result
                    .getRoot();
            ResolvedArtifactNodeVisitor v = new ResolvedArtifactNodeVisitor(
                    modelReader,
                    request.includeParentsAndImports()
                            ? new ParentsAndImportsResolver(
                                    modelReader)
                            : null);
            rootNode.accept(v);
            return v.rootNode;
        } catch (Exception e) {
            StringBuilder msg = new StringBuilder()
                    .append("Could not resolve ").append(rootGavtc).append(" in thread ")
                    .append(Thread.currentThread().getName());
            if (result != null) {
                List<org.eclipse.aether.graph.Dependency> deps = result.getRequest().getManagedDependencies();
                if (deps != null) {
                    msg.append("; with dependencyManagement ");
                    for (org.eclipse.aether.graph.Dependency dep : deps) {
                        msg.append("\n    - ").append(dep);
                    }
                }
            }
            throw new RuntimeException(msg.toString(), e);
        }
    }

    private static void collectConstraints(
            Gav rootBom,
            CachingMavenModelReader modelReader,
            Consumer<org.eclipse.aether.graph.Dependency> add) {
        Model m = modelReader.readEffectiveModel(rootBom);
        ManagedGavsSelector.getManagedDependencies(m).stream()
                .map(d -> {
                    Artifact a = new DefaultArtifact(
                            d.getGroupId(),
                            d.getArtifactId(),
                            d.getClassifier(),
                            d.getType(),
                            d.getVersion());
                    List<org.apache.maven.model.Exclusion> mavenExclusions = d.getExclusions();
                    Collection<Exclusion> exclusions = mavenExclusions == null || mavenExclusions.isEmpty()
                            ? null
                            : mavenExclusions.stream()
                                    .map(e -> new Exclusion(e.getGroupId(), e.getArtifactId(), null, null))
                                    .collect(Collectors.toList());
                    return new org.eclipse.aether.graph.Dependency(
                            a,
                            d.getScope(),
                            Boolean.parseBoolean(d.getOptional()),
                            exclusions);
                })
                .forEach(add);
    }

    static class ParentsAndImportsResolver {
        private final CachingMavenModelReader modelReader;
        private final Deque<StackEntry> stack = new ArrayDeque<>();

        public ParentsAndImportsResolver(CachingMavenModelReader modelReader) {
            super();
            this.modelReader = modelReader;
        }

        public void accept(Artifact a, Consumer<ResolvedArtifactNode.Builder> result) {

            try {
                ModelData resp = modelReader.readModel(a);
                stack.push(new StackEntry(resp.gav(), resp.repositories()));

                /* Predecessors axis */
                if (resp.parent() != null) {
                    /* Ignore the super pom */
                    final Gavtc gav = resp.parent().toGavtc(Type.pom(), null);
                    ResolvedArtifactNode.Builder builder = new ResolvedArtifactNode.Builder(DependencyAxis.PARENT, gav,
                            resp.repositories());
                    traverse(resp.parent(), resp.repositories(), builder::child);
                    result.accept(builder);
                }

                /* Imports axis */
                traverseImports(resp.interpolatedModel(), resp.repositories(), result);
            } catch (Exception e) {
                StringBuilder sb = new StringBuilder("Could not resolve parents and imports in thread ")
                        .append(Thread.currentThread().getName())
                        .append("; dependency stack:");
                for (StackEntry se : stack) {
                    se.append(sb);
                }
                throw new RuntimeException(sb.toString(), e);
            }
            stack.pop();
        }

        void traverseImports(Model interpolatedModel, List<RemoteRepository> repos,
                Consumer<ResolvedArtifactNode.Builder> result) {
            final DependencyManagement dm = interpolatedModel.getDependencyManagement();
            final List<Dependency> deps;
            if (dm != null && (deps = dm.getDependencies()) != null && !deps.isEmpty()) {
                for (Dependency dep : deps) {
                    if ("import".equals(dep.getScope())) {
                        Gavtc gav = new Gavtc(dep.getGroupId(), dep.getArtifactId(), dep.getVersion(), Type.of(dep.getType()),
                                null);
                        ResolvedArtifactNode.Builder builder = new ResolvedArtifactNode.Builder(DependencyAxis.IMPORT, gav,
                                repos);
                        if (!"pom".equals(dep.getType())) {
                            throw new IllegalStateException("Unexpected dependency management import type '" + dep.getType()
                                    + "' of " + gav + " in " + interpolatedModel.getId());
                        }
                        traverse(gav.toGav(), repos, builder::child);
                        result.accept(builder);
                    }
                }
            }
        }

        void traverse(Gav a, List<RemoteRepository> repos, Consumer<ResolvedArtifactNode.Builder> result) {
            stack.push(new StackEntry(a, repos));
            ModelData resp = modelReader.readModel(a);

            /* Predecessors axis */
            if (resp.parent() != null) {
                /* Ignore the super pom */
                Gavtc gav = resp.parent().toGavtc(Type.pom(), null);
                ResolvedArtifactNode.Builder builder = new ResolvedArtifactNode.Builder(DependencyAxis.PARENT, gav, repos);
                traverse(resp.parent(), repos, builder::child);
                result.accept(builder);
            }

            /* Imports axis */
            traverseImports(resp.interpolatedModel(), repos, result);
            stack.pop();
        }

        static record StackEntry(Gav gav, List<RemoteRepository> repositories) {
            StringBuilder append(StringBuilder sb) {
                sb.append("\n    -> ").append(gav);
                for (RemoteRepository repo : repositories) {
                    final String url = repo.getUrl();
                    sb.append("\n        ").append(url);
                    if (!url.endsWith("/")) {
                        sb.append('/');
                    }
                    sb.append(gav.getGroupId().replace('.', '/'))
                            .append('/').append(gav.getArtifactId())
                            .append('/').append(gav.getVersion())
                            .append('/').append(gav.getArtifactId()).append('-').append(gav.getVersion()).append(".pom");
                }
                return sb;
            }
        }
    }

    static class ResolvedArtifactNodeVisitor implements DependencyVisitor {

        private final Deque<ResolvedArtifactNode.Builder> stack = new ArrayDeque<>();
        private ResolvedArtifactNode rootNode;
        private final CachingMavenModelReader modelReader;
        private final ParentsAndImportsResolver parentsAndImportsResolver;

        public ResolvedArtifactNodeVisitor(CachingMavenModelReader modelReader,
                ParentsAndImportsResolver parentsAndImportsResolver) {
            super();
            this.modelReader = modelReader;
            this.parentsAndImportsResolver = parentsAndImportsResolver;
        }

        @Override
        public boolean visitEnter(DependencyNode node) {
            final Artifact artifact = node.getArtifact();
            final Gavtc gavtc = JrebuildUtils.toGavtc(artifact);
            final List<RemoteRepository> repositories = List.copyOf(node.getRepositories());
            modelReader.register(gavtc.toGav(), repositories);
            final ResolvedArtifactNode.Builder newNode = new ResolvedArtifactNode.Builder(DependencyAxis.DEPENDENCY, gavtc,
                    repositories);
            final ResolvedArtifactNode.Builder parent = stack.peek();
            if (parent != null) {
                parent.child(newNode);
            }
            stack.push(newNode);
            if (parentsAndImportsResolver != null) {
                try {
                    parentsAndImportsResolver.accept(artifact, newNode::child);
                } catch (Exception e) {
                    StringBuilder sb = new StringBuilder("Could not resolve parents and imports of ")
                            .append(gavtc)
                            .append(" in thread ")
                            .append(Thread.currentThread().getName())
                            .append("; dependency stack:");
                    for (ResolvedArtifactNode.Builder se : stack) {
                        se.append(sb);
                    }
                    throw new RuntimeException(sb.toString(), e);
                }
            }
            return true;
        }

        @Override
        public boolean visitLeave(DependencyNode node) {
            ResolvedArtifactNode.Builder rn = stack.pop();
            if (stack.isEmpty()) {
                this.rootNode = rn.build();
            }
            return true;
        }

    }

    static class ExcludesDependencySelector implements DependencySelector {

        private Collection<GavtcsPattern> excludes;

        public ExcludesDependencySelector(Collection<GavtcsPattern> excludes) {
            this.excludes = excludes;
        }

        @Override
        public boolean selectDependency(org.eclipse.aether.graph.Dependency dep) {
            final Artifact a = dep.getArtifact();
            return !excludes.stream()
                    .anyMatch(pattern -> pattern.matches(
                            a.getGroupId(),
                            a.getArtifactId(),
                            a.getVersion(),
                            a.getExtension(),
                            a.getClassifier(),
                            dep.getScope()));
        }

        @Override
        public DependencySelector deriveChildSelector(DependencyCollectionContext context) {
            return this;
        }

    }

}
