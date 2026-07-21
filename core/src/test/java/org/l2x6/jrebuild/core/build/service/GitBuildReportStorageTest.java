package org.l2x6.jrebuild.core.build.service;

import io.vertx.mutiny.core.Vertx;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.assertj.core.api.Assertions;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.junit.jupiter.api.Test;
import org.l2x6.jrebuild.api.os.Arch;
import org.l2x6.jrebuild.api.os.Os;
import org.l2x6.jrebuild.api.os.Shell;
import org.l2x6.jrebuild.api.scm.FqScmRef;
import org.l2x6.jrebuild.api.scm.ScmRef;
import org.l2x6.jrebuild.api.scm.ScmRef.Kind;
import org.l2x6.jrebuild.api.scm.ScmRepository;
import org.l2x6.jrebuild.common.git.GitUtils;
import org.l2x6.jrebuild.core.build.BuildGroup;
import org.l2x6.jrebuild.core.build.BuildReport;
import org.l2x6.jrebuild.core.build.BuildRequest;
import org.l2x6.jrebuild.core.build.Reproducibility;
import org.l2x6.jrebuild.core.build.ResourceMatch;
import org.l2x6.jrebuild.core.build.ResourceMatchLevel;
import org.l2x6.jrebuild.core.scm.CloneDirectoriesLayout;
import org.l2x6.pom.tuner.model.Gavtc;
import org.l2x6.pom.tuner.model.OptionalWithDefault;

public class GitBuildReportStorageTest {

    @Test
    void e2e() throws Exception {
        String authorName = "test";
        String authorEmail = "test@test.org";

        Path remoteDir = Path.of("target/GitBuildReportStorageTest/remote").toAbsolutePath().normalize();
        Files.createDirectories(remoteDir);

        final String branch = "main";
        try (Git remoteGit = Git.init().setDirectory(remoteDir.toFile()).setInitialBranch(branch).call()) {

            Path readme = remoteDir.resolve("README.adoc");
            Files.writeString(readme, "Hello");
            GitUtils.commit(remoteGit, "init", authorName, authorEmail);

            String gitUri = remoteDir.resolve(".git").toUri().toString();

            Path clonesDir = Path.of("target/GitBuildReportStorageTest/clones").toAbsolutePath().normalize();
            Files.createDirectories(clonesDir);
            CloneDirectoriesLayout cloneDirectoriesLayout = new CloneDirectoriesLayout(clonesDir);

            Vertx vertx = Vertx.vertx();

            BuildReportStorage storage = BuildReportStorage.git(
                    vertx.fileSystem(),
                    gitUri,
                    branch,
                    authorName,
                    authorEmail,
                    new UsernamePasswordCredentialsProvider("foo", "bar"),
                    5,
                    cloneDirectoriesLayout);

            Gavtc gavtc = Gavtc.of("org.foo:foo:1.2.3:jar");
            FqScmRef scmRef = new FqScmRef(new ScmRef(Kind.TAG, "1.2.3", null), new ScmRepository("?", "git", gitUri));
            BuildGroup bg = new BuildGroup(
                    scmRef,
                    Set.of(gavtc));
            BuildRequest request = new BuildRequest(
                    bg,
                    Os.LINUX,
                    Arch.amd64,
                    Shell.BASH,
                    List.of(),
                    "mvn clean deploy");
            BuildReport report = buildReport(gavtc, request, "2007-12-03T09:15:30.000000000Z");

            {
                List<BuildReport> reports = storage.list(scmRef).collect().asList().await().indefinitely();
                Assertions.assertThat(reports).isEmpty();
            }
            {
                /* Once again */
                List<BuildReport> reports = storage.list(scmRef).collect().asList().await().indefinitely();
                Assertions.assertThat(reports).isEmpty();
            }

            {
                /* Store something */
                BuildReport stored = storage.store(report).await().indefinitely();
                Assertions.assertThat(stored).isSameAs(report);

                List<BuildReport> reports = storage.list(scmRef).collect().asList().await().indefinitely();
                Assertions.assertThat(reports).containsExactly(report);

                /* Refresh the remote working copy */
                remoteGit.reset().setMode(ResetType.HARD).setRef("HEAD").call();
                final Path reportFile = remoteDir.resolve(GitUtils.uriToFileName(gitUri))
                        .resolve("1.2.3/build-report-2007-12-03T09-15-30.yaml");
                /* And make sure the report was pushed by GitBuildReportStorage */
                Assertions.assertThat(reportFile).isRegularFile();
            }

            {
                /* Store a report in the remote, as if some other agent did it */
                BuildReport remoteReport = buildReport(gavtc, request, "2007-12-03T09:15:31.000000000Z");
                BuildReportStorage localStorage = BuildReportStorage.local(vertx.fileSystem(), remoteDir);
                localStorage.store(remoteReport).await().indefinitely();
                GitUtils.commit(remoteGit, "background commit", authorName, authorEmail);

                /* ... then make sure, that GitBuildReportStorage is able to rebase on list() */
                {
                    List<BuildReport> reports = storage.list(scmRef).collect().asList().await().indefinitely();
                    Assertions.assertThat(reports).containsExactlyInAnyOrder(remoteReport, report);
                }

                /* Store one more report in the remote, as if some other agent did it */
                BuildReport remoteReport2 = buildReport(gavtc, request, "2007-12-03T09:15:32.000000000Z");
                localStorage.store(remoteReport2).await().indefinitely();
                GitUtils.commit(remoteGit, "background commit 2", authorName, authorEmail);

                /* ... then make sure, that GitBuildReportStorage is able to rebase when the first push is not successful */
                BuildReport report2 = buildReport(gavtc, request, "2007-12-03T09:15:33.000000000Z");
                BuildReport stored = storage.store(report2).await().indefinitely();
                Assertions.assertThat(stored).isSameAs(report2);

                {
                    List<BuildReport> reports = storage.list(scmRef).collect().asList().await().indefinitely();
                    Assertions.assertThat(reports).containsExactlyInAnyOrder(report2, remoteReport2, remoteReport, report);
                }

                /* Refresh the remote working copy */
                remoteGit.reset().setMode(ResetType.HARD).setRef("HEAD").call();
                final Path reportFile = remoteDir.resolve(GitUtils.uriToFileName(gitUri))
                        .resolve("1.2.3/build-report-2007-12-03T09-15-33.yaml");
                /* And make sure the report was pushed by GitBuildReportStorage */
                Assertions.assertThat(reportFile).isRegularFile();

            }

        }
    }

    private BuildReport buildReport(Gavtc gavtc, BuildRequest request, CharSequence ts) {
        Map<Gavtc, ResourceMatch> builtArtifacts = new TreeMap<>(
                Gavtc.groupFirstComparator(OptionalWithDefault.valueOrDefaultComparator()));
        builtArtifacts.put(gavtc, new ResourceMatch(gavtc.getRepositoryPath(), ResourceMatchLevel.PERFECT, null, List.of()));
        return new BuildReport(
                request,
                "deadbeef",
                Reproducibility.PERFECT,
                ZonedDateTime.parse(ts),
                Duration.ofSeconds(42),
                builtArtifacts,
                null);
    }
}
