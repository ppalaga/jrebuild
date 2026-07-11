package org.l2x6.jrebuild.core.build.service;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;
import org.eclipse.jgit.api.Git;
import org.l2x6.jrebuild.api.scm.FqScmRef;
import org.l2x6.jrebuild.common.git.GitUtils;
import org.l2x6.jrebuild.core.build.BuildGroup;
import org.l2x6.jrebuild.core.maven.ReferenceMavenRepository;
import org.l2x6.jrebuild.core.scm.CloneDirectoriesLayout;
import org.l2x6.jrebuild.core.scm.CloneDirectoriesLayout.CloneDirectory;
import org.l2x6.pom.tuner.ExpressionEvaluator;
import org.l2x6.pom.tuner.MavenSourceTree;
import org.l2x6.pom.tuner.MavenSourceTree.ActiveProfiles;
import org.l2x6.pom.tuner.model.Gav;
import org.l2x6.pom.tuner.model.Gavtc;

public class FindReferenceArtifactsService {
    private final CloneDirectoriesLayout cloneDirectoriesLayout;
    private final ReferenceMavenRepository referenceMavenRepository;

    public FindReferenceArtifactsService(
            CloneDirectoriesLayout cloneDirectoriesLayout,
            ReferenceMavenRepository referenceMavenRepository) {
        super();
        this.cloneDirectoriesLayout = Objects.requireNonNull(cloneDirectoriesLayout, "cloneDirectoriesLayout");
        this.referenceMavenRepository = Objects.requireNonNull(referenceMavenRepository, "referenceMavenRepository");
    }

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
     * @param  fqScmRef              the SCM tag to collect the published artifacts for
     * @param  sourceRootDirectories
     * @return                       a new {@link BuildGroup} containing the artifacts published to
     *                               {@link #referenceMavenRepository}
     *                               from the respository represented by {@code fqScmRef}
     */
    public Uni<BuildGroup> findPublishedArtifacts(FqScmRef fqScmRef, Function<Path, List<Path>> sourceRootDirectories) {

        Uni<Set<Gav>> sourceTreeGavs = cloneDirectoriesLayout.lockDirectory(fqScmRef.repository().uri())
                .chain(cloneDir -> Uni.createFrom()
                        .item(() -> cloneAndList(fqScmRef, sourceRootDirectories, cloneDir))
                        .runSubscriptionOn(Infrastructure.getDefaultWorkerPool()) // cloneAndList are blocking ops
                        .eventually(cloneDir::close) //
                );

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

    Set<Gav> cloneAndList(FqScmRef fqScmRef, Function<Path, List<Path>> sourceRootDirectories, CloneDirectory cloneDir) {
        // Clone or fetch to workingCopyDir
        try (@SuppressWarnings("unused")
        Git git = GitUtils.cloneOrFetchAndReset(fqScmRef, cloneDir.cloneDirectory(), 1)) {
        }
        // List Maven modules present in workingCopyDir
        return listModules(cloneDir.cloneDirectory(), sourceRootDirectories);

    }

    Set<Gav> listModules(Path workingCopyDir, Function<Path, List<Path>> sourceRootDirectories) {
        Set<Gav> result = new TreeSet<>();
        for (Path relRoot : sourceRootDirectories.apply(workingCopyDir)) {
            listModules(workingCopyDir.resolve(relRoot).resolve("pom.xml"), result::add);
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
