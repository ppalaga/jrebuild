package org.l2x6.jrebuild.core.build.service;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

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

public record FindPublishedArtifactsService(Path gitCloneBaseDir, ReferenceMavenRepository referenceMavenRepository) {

    /**
     * Checks out the given {@code fqScmRef} to {@code gitCloneBaseDir.resolve(fqScmRef.scmRef().uri())} using
     * {@link GitUtils#cloneOrFetchAndReset(String, String, Path, int)}.
     * If there is a {@code pom.xml} file somwhere around the root directory of the working copy, collects the {@link Gav}s
     * present
     * in the source tree using {@link #listModules(Path)}.
     * For each of those {@link Gav}s, checks, whether the corresponding
     * {@link Gavtc} with type {@code pom} and {@code classifier} {@code null}
     * ({@code Gavtc gavtc = gav.toGavtc(org.l2x6.pom.tuner.model.Gavtc.Type.pom(), null} exists in
     * {@link #referenceMavenRepository}
     * and if it exists, adds it to the resulting {@link BuildGroup}.
     *
     * @param  fqScmRef the SCM tag to collect the published artifacts for
     * @return          a new {@link BuildGroup} containing the artifacts published to {@link #referenceMavenRepository}
     *                  from the respository represented by {@code fqScmRef}
     */
    public Uni<BuildGroup> findPublishedArtifacts(FqScmRef fqScmRef) {
        Path workingCopyDir = gitCloneBaseDir.resolve(GitUtils.uriToFileName(fqScmRef.repository().uri()));
        try {
            Files.createDirectories(workingCopyDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create "+ workingCopyDir, e);
        }

        try (Git git = GitUtils.cloneOrFetchAndReset(fqScmRef, workingCopyDir, 1)) {
        }

        Path rootPomXml = workingCopyDir.resolve("pom.xml");
        if (!Files.exists(rootPomXml)) {
            return Uni.createFrom().item(BuildGroup.builder(fqScmRef).build());
        }

        Set<Gav> gavs = listModules(rootPomXml);

        return Multi.createFrom().iterable(gavs)
                .onItem().transformToUniAndMerge(gav -> {
                    Gavtc gavtc = gav.toGavtc(Gavtc.Type.pom(), null);
                    return referenceMavenRepository.resolve(gavtc)
                            .map(gavtcf -> gavtcf.toGavtc())
                            .onFailure().recoverWithNull();
                })
                .filter(Objects::nonNull)
                .collect().asList()
                .map(publishedGavtcs -> {
                    BuildGroup.Builder builder = BuildGroup.builder(fqScmRef);
                    publishedGavtcs.forEach(builder::artifact);
                    return builder.build();
                });
    }

    Set<Gav> listModules(Path rootPomXml) {
        MavenSourceTree tree = MavenSourceTree.of(rootPomXml);
        ExpressionEvaluator expressionEvaluator = tree.getExpressionEvaluator(ActiveProfiles.all());
        TreeSet<Gav> gavs = tree.getModulesByGa().values().stream()
                .map(module -> expressionEvaluator.evaluateGav(module.getGav()))
                .collect(Collectors.toCollection(TreeSet::new));
        return Collections.unmodifiableSet(gavs);
    }
}
