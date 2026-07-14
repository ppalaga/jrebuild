package org.l2x6.jrebuild.core.build.service;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.l2x6.jrebuild.api.os.OsArch;
import org.l2x6.jrebuild.api.os.Tool;
import org.l2x6.jrebuild.api.scm.FqScmRef;
import org.l2x6.jrebuild.api.scm.ScmRef;
import org.l2x6.jrebuild.api.scm.ScmRef.Kind;
import org.l2x6.jrebuild.api.scm.ScmRepository;
import org.l2x6.jrebuild.core.build.BuildReport;
import org.l2x6.jrebuild.core.build.BuildRequest;
import org.l2x6.jrebuild.core.build.Reproducibility;
import org.l2x6.jrebuild.core.build.SourceRootDirectories;
import org.l2x6.jrebuild.core.build.service.TestEnvironment.RemoteRepository;

public class LocalRebuildServiceTest {

    @Test
    void e2e() throws Exception {
        try (TestEnvironment testEnv = new TestEnvironment(LocalRebuildServiceTest.class, RemoteRepository.CENTRAL)) {
            FindReferenceArtifactsService findService = testEnv.getFindReferenceArtifactsService();

            List<Tool> tools = List.of(new Tool("sdkman", "java", "11.0.25-tem"));
            String script = "./mvnw clean deploy -Prelease -DskipTests -Dgpg.skip -DskipPublishing=true deploy:deploy -DaltDeploymentRepository=local::${DEPLOYMENT_REPO}";

            BuildReport report = findService.findPublishedArtifacts(
                    new FqScmRef(
                            new ScmRef(Kind.TAG, "4.10.0", null),
                            new ScmRepository("?", "git", "https://github.com/l2x6/pom-tuner.git")),
                    SourceRootDirectories.root())
                    .chain(bg -> {
                        OsArch currentOsArch = OsArch.current();
                        BuildRequest buildRequest = new BuildRequest(bg, currentOsArch.os(), currentOsArch.arch(),
                                currentOsArch.os().defaultShell(),
                                tools, script);
                        LocalRebuildService rebuildService = testEnv.getLocalRebuildService();
                        return rebuildService.ensureBuilt(buildRequest, Reproducibility.PERFECT);
                    }).await().indefinitely();

        }
    }
}
