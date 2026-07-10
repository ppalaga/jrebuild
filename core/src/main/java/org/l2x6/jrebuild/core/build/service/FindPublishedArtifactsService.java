package org.l2x6.jrebuild.core.build.service;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.l2x6.jrebuild.api.scm.FqScmRef;
import org.l2x6.jrebuild.common.git.GitUtils;
import org.l2x6.jrebuild.core.build.BuildGroup;
import org.l2x6.jrebuild.core.maven.ReferenceMavenRepository;
import org.l2x6.pom.tuner.ExpressionEvaluator;
import org.l2x6.pom.tuner.MavenSourceTree;
import org.l2x6.pom.tuner.MavenSourceTree.ActiveProfiles;
import org.l2x6.pom.tuner.model.Expression;
import org.l2x6.pom.tuner.model.Gav;
import org.l2x6.pom.tuner.model.GavExpression;

import io.smallrye.mutiny.Uni;

public record FindPublishedArtifactsService(Path gitCloneBaseDir, ReferenceMavenRepository referenceMavenRepository) {

    /**
     * Checks out the given {@code fqScmRef} to {@code gitCloneBaseDir.resolve(fqScmRef.scmRef().uri())} using
     * {@link GitUtils#cloneOrFetchAndReset(String, String, Path, int)}.
     * If there is a {@code pom.xml} file somwhere around the root directory of the working copy, collects the {@link Gav}s present
     * in the source tree using {@link #listModules(Path)}.
     * For each of those {@link Gav}s, checks, whether the corresponding
     * {@link Gavtc} with type {@code pom} and {@code classifier} {@code null} ({@code Gavtc gavtc = gav.toGavtc(org.l2x6.pom.tuner.model.Gavtc.Type.pom(), null} exists in {@link #referenceMavenRepository}
     * and if it exists, adds it to the resulting {@link BuildGroup}.
     *
     * @param fqScmRef the SCM tag to collect the published artifacts for
     * @return a new {@link BuildGroup} containing the artifacts published to {@link #referenceMavenRepository} from the respository represented by {@code fqScmRef}
     */
    public Uni<BuildGroup> findPublishedArtifacts(FqScmRef fqScmRef) {

    }

    Set<Gav> listModules(Path rootPomXml) {
        MavenSourceTree tree = MavenSourceTree.of(rootPomXml);
        ExpressionEvaluator expressionEvaluator = tree.getExpressionEvaluator(ActiveProfiles.all());
        return Collections.unmodifiableSet(tree.getModulesByGa().values().stream()
            .map(module -> expressionEvaluator.evaluateGav(module.getGav()))
            .collect(Collectors.toCollection(TreeSet::new)));
    }
}
