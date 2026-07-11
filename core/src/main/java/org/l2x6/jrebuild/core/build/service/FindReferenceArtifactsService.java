package org.l2x6.jrebuild.core.build.service;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

import org.eclipse.jgit.api.Git;
import org.l2x6.jrebuild.api.scm.FqScmRef;
import org.l2x6.jrebuild.common.git.GitUtils;
import org.l2x6.jrebuild.core.build.BuildGroup;
import org.l2x6.jrebuild.core.maven.ReferenceMavenRepository;
import org.l2x6.pom.tuner.ExpressionEvaluator;
import org.l2x6.pom.tuner.MavenSourceTree;
import org.l2x6.pom.tuner.MavenSourceTree.ActiveProfiles;
import org.l2x6.pom.tuner.model.Gav;
import org.l2x6.pom.tuner.model.Gavtc;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.vertx.mutiny.core.Vertx;

public record FindReferenceArtifactsService(Vertx vertx, Path gitCloneBaseDir,
        ReferenceMavenRepository referenceMavenRepository) {

    /**
     * Checks out the given {@code fqScmRef} to {@code gitCloneBaseDir.resolve(fqScmRef.scmRef().uri())} using
     * {@link GitUtils#cloneOrFetchAndReset(String, String, Path, int)}.
     * If there is a {@code pom.xml} file somwhere around the root directory of the working copy, collects the
     * {@link Gav}s
     * present
     * in the source tree using {@link #listModules(Path)}.
     * For each of those {@link Gav}s, checks, whether the corresponding
     * {@link Gavtc} with type {@code pom} and {@code classifier} {@code null}
     * ({@code Gavtc gavtc = gav.toGavtc(org.l2x6.pom.tuner.model.Gavtc.Type.pom(), null} exists in
     * {@link #referenceMavenRepository}
     * and if it exists, adds it to the resulting {@link BuildGroup}.
     *
     * @param fqScmRef the SCM tag to collect the published artifacts for
     * @param sourceRootDirectories
     * @return a new {@link BuildGroup} containing the artifacts published to {@link #referenceMavenRepository}
     *         from the respository represented by {@code fqScmRef}
     */
    public Uni<BuildGroup> findPublishedArtifacts(FqScmRef fqScmRef, List<Path> sourceRootDirectories) {
        Path workingCopyDir = gitCloneBaseDir.resolve(GitUtils.uriToFileName(fqScmRef.repository().uri()));

        @SuppressWarnings("unused")
        Uni<Set<Gav>> sourceTreeGavs = vertx.fileSystem()
                .mkdirs(workingCopyDir.toString()) // create workingCopyDir if needed
                .chain(dirCreated -> Uni.createFrom()
                        .item(() -> {
                            // Clone or fetch to workingCopyDir
                            try (@SuppressWarnings("unused")
                            Git git = GitUtils.cloneOrFetchAndReset(fqScmRef, workingCopyDir, 1)) {
                            }
                            // List Maven modules present in workingCopyDir
                            return listModules(workingCopyDir, sourceRootDirectories);
                        })
                        .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())) // the above are blocking ops
                ;

        return sourceTreeGavs.onItem()
                .transformToUni((Set<Gav> gavs) -> Multi.createFrom().iterable(gavs)
                        .onItem()
                        .transformToUniAndMerge(sourceTreeGav ->
                        // for each sourceTreeGavs check in parallel whether it was published to the reference repo
                        referenceMavenRepository.resolve(sourceTreeGav.toGavtc(Gavtc.Type.pom(), null))
                                .map(gavtcf -> gavtcf.toGavtc())
                                .onFailure().recoverWithNull()) // missing items would throw a failure
                        .filter(Objects::nonNull) // remove the missing ones
                        .collect().asList()
                        .map(publishedGavtcs -> BuildGroup.builder(fqScmRef).artifacts(publishedGavtcs).build()));

    }

    Set<Gav> listModules(Path workingCopyDir, List<Path> sourceRootDirectories) {
        Set<Gav> result = new TreeSet<>();
        if (sourceRootDirectories.isEmpty()) {
            listModules(workingCopyDir.resolve("pom.xml"), result::add);
        } else {
            for (Path relRoot : sourceRootDirectories) {
                listModules(workingCopyDir.resolve(relRoot).resolve("pom.xml"), result::add);
            }
        }
        return result;
    }

    void listModules(Path rootPomXml, Consumer<Gav> add) {
        MavenSourceTree tree = MavenSourceTree.of(rootPomXml);
        ExpressionEvaluator expressionEvaluator = tree.getExpressionEvaluator(ActiveProfiles.all());
        tree.getModulesByGa().values().stream()
                .map(module -> expressionEvaluator.evaluateGav(module.getGav()))
                .forEach(add);
    }
}
