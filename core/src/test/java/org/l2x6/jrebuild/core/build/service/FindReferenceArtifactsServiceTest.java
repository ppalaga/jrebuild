package org.l2x6.jrebuild.core.build.service;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.l2x6.jrebuild.api.scm.FqScmRef;
import org.l2x6.jrebuild.api.scm.ScmRef;
import org.l2x6.jrebuild.api.scm.ScmRef.Kind;
import org.l2x6.jrebuild.api.scm.ScmRepository;
import org.l2x6.jrebuild.core.build.BuildGroup;
import org.l2x6.jrebuild.core.build.SourceRootDirectories;
import org.l2x6.pom.tuner.model.Gavtc;

public class FindReferenceArtifactsServiceTest {

    @Test
    void listModulesEmptySourceRoots() throws Exception {

        try (TestEnvironment testEnvironment = new TestEnvironment(getClass())) {
            FindReferenceArtifactsService findService = testEnvironment.getFindReferenceArtifactsService();
            BuildGroup bg = findService.findPublishedArtifacts(
                    new FqScmRef(
                            new ScmRef(Kind.TAG, "4.10.0", null),
                            new ScmRepository("?", "git", "https://github.com/l2x6/pom-tuner.git")),
                    SourceRootDirectories.root())
                    .await().indefinitely();

            Assertions.assertThat(bg.artifacts()).containsExactly(Gavtc.of("org.l2x6.pom-tuner:pom-tuner:4.10.0:pom"));
        }
    }

}
