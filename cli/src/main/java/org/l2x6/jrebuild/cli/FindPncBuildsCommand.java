/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild
 *                                 project contributors as indicated by the @author tags
 *                                 SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.cli;

import eu.maveniverse.maven.mima.context.Context;
import eu.maveniverse.maven.mima.context.ContextOverrides;
import eu.maveniverse.maven.mima.context.Runtime;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import org.jboss.logging.Logger;
import org.l2x6.jrebuild.api.scm.RemoteScmLookup.AggregateRemoteScmLookup;
import org.l2x6.jrebuild.api.util.JrebuildUtils;
import org.l2x6.jrebuild.core.dep.DependencyCollector;
import org.l2x6.jrebuild.core.dep.DependencyCollectorRequest;
import org.l2x6.jrebuild.core.dep.ResolvedArtifactNode;
import org.l2x6.jrebuild.core.dep.ResolvedArtifactNode.DependencyAxis;
import org.l2x6.jrebuild.core.mima.JRebuildRuntime;
import org.l2x6.jrebuild.core.tree.CutStemVisitor;
import org.l2x6.jrebuild.core.tree.Node;
import org.l2x6.jrebuild.core.tree.PrintVisitor;
import org.l2x6.jrebuild.core.tree.Visitor;
import org.l2x6.jrebuild.pnc.PncScmLocator;
import org.l2x6.pom.tuner.model.GavSet;
import org.l2x6.pom.tuner.model.Gavtc;
import org.l2x6.pom.tuner.model.OptionalWithDefault;
import picocli.CommandLine;
import picocli.CommandLine.Mixin;

@CommandLine.Command(name = "find-pnc-builds")
public class FindPncBuildsCommand implements Runnable {
    private static final Logger log = Logger.getLogger(FindPncBuildsCommand.class);

    @Mixin
    RootArtifactsOptions rootArtifactsOptions;

    @Mixin
    PncOptions pncOptions;

    public FindPncBuildsCommand() {
    }

    @Override
    public void run() {

        Instant minPncBuildDate = pncOptions.maxPncBuildDate(rootArtifactsOptions.cacheDir());

        final Path cacheDir = rootArtifactsOptions.cacheDir();

        Runtime runtime = JRebuildRuntime.getInstance();
        ContextOverrides.Builder overrides = ContextOverrides.create();
        try (Context context = runtime.create(overrides.build())) {

            final DependencyCollectorRequest re = rootArtifactsOptions.dependencyCollectorRequest(context);

            final PncScmLocator locator = new PncScmLocator(
                    cacheDir,
                    minPncBuildDate,
                    pncOptions.pncBaseUri,
                    pncOptions.pncIncludeTemporary,
                    new AggregateRemoteScmLookup(/* No real ScmLookup impls here, bc we do not need them */));

            List<PncInfoNode.Builder> roots =
                    /* Collect dependencies of each root artifact */
                    DependencyCollector.collect(context, re)
                            /* Now we have a stream of artifact trees (one per root artifact) */

                            /* Cut the stem */
                            .onItem()
                            .transformToMulti(
                                    resolvedArtifact -> cutStemVisitor(rootArtifactsOptions.stem).walk(resolvedArtifact)
                                            .result())
                            .merge()
                            /* Now we have a stream of artifact trees (roughly one per root artifact) with stems cut away */

                            // .onItem().invoke(resolvedArtifact -> log.infof("Resolved:\n%s", PrintVisitor.toString(resolvedArtifact)))

                            .select().distinct()

                            /* Now check whether the collected artifacts are built in PNC */
                            .onItem()
                            .transformToUniAndMerge(resolvedArtifact -> {

                                return Uni.createFrom()
                                        .item(() -> new PncBuildVisitor(locator).walk(resolvedArtifact).rootNode())
                                        .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());

                            })

                            .select().distinct()

                            //                    .onItem()
                            //                    .invoke(p -> log.infof("Scm Repos:\n%s", PrintVisitor.toString(p)))
                            .onFailure().invoke(e -> log.error(e.getMessage(), e))

                            .collect().asList()
                            .await().indefinitely();
            ;

            /* Now merge the list of SCM trees into a single tree under a a virtual root node */
            final PncInfoNode.Builder forest = new PncInfoNode.Builder(DependencyAxis.DEPENDENCY, Gavtc.of("root:root:0.0.0"),
                    null);
            for (PncInfoNode.Builder root : roots) {
                log.infof("Merging " + root);
                forest.adopt(root);
            }
            PncInfoNode result = forest.build();
            log.infof("Final tree:\n\n %s", PrintVisitor.<PncInfoNode> stringBuilderPrintVisitor().walk(result).toString());

        }
    }

    public static <THIS extends CutStemVisitor<ResolvedArtifactNode, THIS>> CutStemVisitor<ResolvedArtifactNode, THIS> cutStemVisitor(
            GavSet stem) {
        return new CutStemVisitor<>(node -> stem.contains(node.gavtc().toGav()));
    }

    public static class PncBuildVisitor implements Visitor<ResolvedArtifactNode, PncBuildVisitor> {

        private final Deque<PncInfoNode.Builder> stack = new ArrayDeque<>();
        private PncInfoNode.Builder rootNode;
        private final PncScmLocator locator;

        public PncBuildVisitor(PncScmLocator locator) {
            super();
            this.locator = locator;
        }

        @Override
        public boolean enter(ResolvedArtifactNode node) {
            String latestPncVersion = locator.latestPncVersion(node.gavtc());
            PncInfoNode.Builder newNode = new PncInfoNode.Builder(node.axis(), node.gavtc(), latestPncVersion);
            if (!stack.isEmpty()) {
                PncInfoNode.Builder parent = stack.peek();
                parent.child(newNode);
            }
            stack.push(newNode);
            return true;
        }

        @Override
        public boolean leave(ResolvedArtifactNode node) {
            PncInfoNode.Builder rn = stack.peek();
            stack.pop();
            if (stack.isEmpty()) {
                this.rootNode = rn;
            }
            return true;
        }

        public PncInfoNode.Builder rootNode() {
            return rootNode;
        }
    }

    public static class PncInfoNode implements Node<PncInfoNode>, Comparable<PncInfoNode> {
        private static final Comparator<Gavtc> COMPARATOR = Gavtc
                .groupFirstComparator(OptionalWithDefault.valueOrDefaultComparator());
        private final DependencyAxis axis;
        private final Gavtc gavtc;
        private final String latestPncVersion;
        private List<PncInfoNode> children;
        private final int hashCode;

        private PncInfoNode(DependencyAxis axis, Gavtc rootGavtc, String latestPncVersion, List<PncInfoNode> children) {
            super();
            this.axis = axis;
            this.gavtc = rootGavtc;
            this.latestPncVersion = latestPncVersion;
            this.children = JrebuildUtils.assertImmutable(children);
            this.hashCode = 31 * (31 * gavtc.hashCode() + children.hashCode())
                    + (latestPncVersion == null ? 0 : latestPncVersion.hashCode());
        }

        @Override
        public List<PncInfoNode> children() {
            return children;
        }

        @Override
        public int compareTo(PncInfoNode o) {
            return COMPARATOR.compare(gavtc, o.gavtc);
        }

        public String toString() {
            return (latestPncVersion != null ? "✅ " : "❌ ") + axis + gavtc.toString() + "/" + latestPncVersion;
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
            PncInfoNode other = (PncInfoNode) obj;
            return gavtc.equals(other.gavtc) && children.equals(other.children)
                    && latestPncVersion.equals(other.latestPncVersion);
        }

        StringBuilder append(StringBuilder sb) {
            return append(sb, axis, gavtc, latestPncVersion);
        }

        static StringBuilder append(StringBuilder sb, DependencyAxis axis, Gavtc gavtc, String latestPncVersion) {
            sb.append("\n    -> ").append(latestPncVersion != null ? "✅ " : "❌ ").append(axis).append(gavtc).append("/")
                    .append(latestPncVersion);
            return sb;
        }

        public static class Builder implements Node<Builder> {
            private final DependencyAxis axis;
            private final Gavtc gavtc;
            private final String latestPncVersion;
            private final List<Builder> children = new ArrayList<>();

            public Builder(DependencyAxis axis, Gavtc gavtc, String latestPncVersion) {
                super();
                this.axis = axis;
                this.gavtc = Objects.requireNonNull(gavtc);
                this.latestPncVersion = latestPncVersion;
            }

            public Builder child(Builder child) {
                children.add(child);
                return this;
            }

            @Override
            public Collection<Builder> children() {
                return children;
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

            public PncInfoNode build() {
                return new PncInfoNode(
                        axis,
                        gavtc,
                        latestPncVersion,
                        Collections.unmodifiableList(
                                children.stream()
                                        .map(Builder::build)
                                        .toList()));
            }

            StringBuilder append(StringBuilder sb) {
                return PncInfoNode.append(sb, axis, gavtc, latestPncVersion);
            }

            public String toString() {
                return (latestPncVersion != null ? "✅" : "❌") + axis + " " + gavtc.toString() + "/" + latestPncVersion;
            }

        }

    }

}
