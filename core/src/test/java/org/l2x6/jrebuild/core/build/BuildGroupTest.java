package org.l2x6.jrebuild.core.build;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.l2x6.jrebuild.api.scm.FqScmRef;
import org.l2x6.jrebuild.api.scm.ScmRef;
import org.l2x6.jrebuild.api.scm.ScmRef.Kind;
import org.l2x6.jrebuild.api.scm.ScmRepository;
import org.l2x6.pom.tuner.model.Gav;
import org.l2x6.pom.tuner.model.Gavtc;

public class BuildGroupTest {

    @Test
    void findMainGroupId() {

        FqScmRef scmRef = new FqScmRef(new ScmRef(Kind.TAG, "1.2.3", "deadbeef"),
                new ScmRepository("?", "git", "https://github.com/org/project.git"));
        //        {
        //            BuildGroup g = BuildGroup.builder(scmRef)
        //                    .artifact(Gavtc.of("foo:f1:1.2.3"))
        //                    .artifact(Gavtc.of("foo:f2:1.2.3"))
        //                    .artifact(Gavtc.of("bar:f1:1.2.3"))
        //                    .artifact(Gavtc.of("bar:f2:1.2.3"))
        //                    .build();
        //            Assertions.assertThat(g.findMainArtifact()).isEqualTo(Gav.of("bar:f:1.2.3"));
        //        }

        {
            BuildGroup g = BuildGroup.builder(scmRef)
                    .artifact(Gavtc.of("foo:f1:1.2.3"))
                    .artifact(Gavtc.of("foo:f2:1.2.3"))
                    .artifact(Gavtc.of("bar:f1:1.2.3"))
                    .artifact(Gavtc.of("baz:f2:1.2.3"))
                    .build();
            Assertions.assertThat(g.findMainArtifact()).isEqualTo(Gav.of("foo:f:1.2.3"));
        }

        {
            BuildGroup g = BuildGroup.builder(scmRef)
                    .artifact(Gavtc.of("foo:f1:1.2.3"))
                    .build();
            Assertions.assertThat(g.findMainArtifact()).isEqualTo(Gav.of("foo:f1:1.2.3"));
        }

    }
}
