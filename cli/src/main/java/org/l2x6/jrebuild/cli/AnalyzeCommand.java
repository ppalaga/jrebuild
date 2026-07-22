/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.cli;

import eu.maveniverse.maven.mima.context.Context;
import eu.maveniverse.maven.mima.context.ContextOverrides;
import eu.maveniverse.maven.mima.context.Runtime;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.jboss.logging.Logger;
import org.l2x6.jrebuild.api.scm.FqScmRef.AnnotatedFqScmRef;
import org.l2x6.jrebuild.api.scm.RemoteScmLookup;
import org.l2x6.jrebuild.core.build.BuildGroup;
import org.l2x6.jrebuild.core.dep.DependencyCollector;
import org.l2x6.jrebuild.core.dep.DependencyCollectorRequest;
import org.l2x6.jrebuild.core.dep.ResolvedArtifactNode;
import org.l2x6.jrebuild.core.mima.JRebuildRuntime;
import org.l2x6.jrebuild.core.mima.internal.CachingMavenModelReader;
import org.l2x6.jrebuild.core.scm.GitRemoteScmLookup;
import org.l2x6.jrebuild.core.scm.ScmRepositoryService;
import org.l2x6.jrebuild.core.scm.ScmRepositoryService.ScmInfoNode;
import org.l2x6.jrebuild.core.tree.CutStemVisitor;
import org.l2x6.jrebuild.core.tree.PrintVisitor;
import org.l2x6.pom.tuner.model.Gav;
import org.l2x6.pom.tuner.model.GavSet;
import picocli.CommandLine;
import picocli.CommandLine.Mixin;

@CommandLine.Command(name = "analyze")
public class AnalyzeCommand implements Runnable {
    private static final Logger log = Logger.getLogger(AnalyzeCommand.class);

    @Mixin
    RootArtifactsOptions rootArtifactsOptions;

    @Mixin
    PncOptions pncOptions;

    @CommandLine.Option(names = {
            "--buildspec-clone-dir" },
            description = "A directory where to clone remote Domino and Reproducible Central recipes",
            defaultValue = "~/.m2/buildspec")
    Path dominoCloneDir;

    @CommandLine.Option(names = {
            "--domino-recipes-urls" }, description = "A list of Git URLs hosting Domino build recipes", split = ",")
    Set<String> dominoRecipeUrls = Set.of();

    @CommandLine.Option(names = {
            "--reproducible-central-urls" },
            description = "A list of Git URLs hosting Reproducible Central buildspecs, such as https://github.com/jvm-repo-rebuild/reproducible-central.git",
            split = ",")
    Set<String> reproducibleCentralUrls = Set.of();

    @CommandLine.Option(names = {
            "--ls-remotes-older-than" },
            description = """
                    A timestamp in 2025-12-01T10:15:30Z format determining how fresh the entries in the local ls-remotes-cache must be.
                    You should typically set this to the release date of the root artifacts you are analyzing.
                    E.g. if you are analyzing artigfacts from a project that was released on 2025-12-01T10:15:30Z,
                    then it is fine to set --ls-remotes-older-than=2025-12-01T10:15:30Z
                    because it should be fine to assume that all its dependencies were tagged before that date.
                    If not specified, then it is set to first the execution time on the given day.
                    Use --ls-remotes-older-than=now to force refreshing the entries in ls-remotes-cache.
                    """)
    String rawMinRetievalTime;
    Instant minRetievalTime;

    public AnalyzeCommand() {
    }

    @Override
    public void run() {

        final Path cacheDir = rootArtifactsOptions.cacheDir();

        dominoCloneDir = rootArtifactsOptions.resolveHome(dominoCloneDir);
        final Path lsRemotesCache = cacheDir.resolve("ls-remotes-cache.txt");

        if (rawMinRetievalTime == null) {
            minRetievalTime = BaseOptions.defaultMinRetrievalTime("ls-remotes-older-than", cacheDir);
        } else if ("now".equals(rawMinRetievalTime)) {
            minRetievalTime = Instant.now();
        } else {
            minRetievalTime = Instant.parse(rawMinRetievalTime);
        }

        Runtime runtime = JRebuildRuntime.getInstance();
        ContextOverrides.Builder overrides = ContextOverrides.create();
        try (Context context = runtime.create(overrides.build())) {

            final DependencyCollectorRequest re = rootArtifactsOptions.dependencyCollectorRequest(context);

            try (RemoteScmLookup.AggregateRemoteScmLookup remoteScm = new RemoteScmLookup.AggregateRemoteScmLookup(
                    new GitRemoteScmLookup(lsRemotesCache, minRetievalTime))) {
                final ScmRepositoryService locator = ScmRepositoryService.create(
                        context.lookup().lookup(CachingMavenModelReader.class).get()::readEffectiveModel,
                        remoteScm,
                        dominoCloneDir,
                        cacheDir,
                        pncOptions.maxPncBuildDate(cacheDir),
                        reproducibleCentralUrls,
                        dominoRecipeUrls,
                        pncOptions.pncBaseUri,
                        pncOptions.pncIncludeTemporary);

                List<ScmInfoNode> roots =
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

                                /* Map each artifact of each artifact tree to its SCM repository thus becoming a stream of trees of interdependent SCM repos */
                                .onItem()
                                .transformToUniAndMerge(resolvedArtifact -> {

                                    return Uni.createFrom().item(() -> locator.newVisitor().walk(resolvedArtifact).rootNode())
                                            .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());

                                })

                                .select().distinct()

                                .onItem()
                                .invoke(p -> log.infof("Scm Repos:\n%s", PrintVisitor.toString(p)))
                                .onFailure().invoke(e -> log.error(e.getMessage(), e))

                                .collect().asList()
                                .await().indefinitely();
                ;

                /* Now merge the list of SCM trees into a single tree under a a virtual root node */
                final ScmInfoNode.Builder forest = ScmInfoNode
                        .builder(new BuildGroup.Builder<AnnotatedFqScmRef>(
                                AnnotatedFqScmRef.createUnknown(Gav.of("root:root:0.0.0"))));
                for (ScmInfoNode root : roots) {
                    log.infof("Merging " + root);
                    forest.adopt(root.builder());
                }
                ScmInfoNode result = forest.build();
                log.infof("Final tree:\n\n %s", PrintVisitor.<ScmInfoNode> stringBuilderPrintVisitor().walk(result).toString());
            }
        }
    }

    public static <THIS extends CutStemVisitor<ResolvedArtifactNode, THIS>> CutStemVisitor<ResolvedArtifactNode, THIS> cutStemVisitor(
            GavSet stem) {
        return new CutStemVisitor<>(node -> stem.contains(node.gavtc().toGav()));
    }

}
