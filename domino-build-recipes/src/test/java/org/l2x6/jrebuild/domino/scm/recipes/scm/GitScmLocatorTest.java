/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.domino.scm.recipes.scm;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.l2x6.jrebuild.domino.scm.recipes.location.RecipeGroupManager;
import org.l2x6.pom.tuner.model.Gav;

import static org.assertj.core.api.Assertions.assertThat;

class GitScmLocatorTest {
    private static final Logger log = Logger.getLogger(GitScmLocatorTest.class);
    static String gitRepoUri;
    static Path gitRepoCloneDir;

    @BeforeAll
    static void beforeAll() throws IOException, IllegalStateException, GitAPIException {
        final Path sourceDir = Path.of("src/test/resources/git-recipes");
        UUID uuid = UUID.randomUUID();
        Path gitRepoSourceDir = Path.of("target/git-recipes-source-repo-" + uuid).toAbsolutePath();
        gitRepoUri = gitRepoSourceDir.resolve(".git").toUri().toString();
        Files.createDirectories(gitRepoSourceDir);
        gitRepoCloneDir = Path.of("target/git-recipes-clone-" + uuid).toAbsolutePath();
        Files.createDirectories(gitRepoCloneDir);
        Files.walkFileTree(sourceDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path targetDir = gitRepoSourceDir.resolve(sourceDir.relativize(dir));
                if (!Files.exists(targetDir)) {
                    Files.createDirectories(targetDir);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, gitRepoSourceDir.resolve(sourceDir.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });

        /* Create a test repo that we will clone later */
        Git.init().setDirectory(gitRepoSourceDir.toFile()).setInitialBranch("main").call();
        try (Git git = Git.open(gitRepoSourceDir.toFile())) {
            git.add().addFilepattern(".").call();
            git.commit().setAuthor("Test", "foo@localhost").setMessage("Init").call();
        }

    }

    @Test
    void lookupScmInfoRelaxNG() {
        TagInfo tag = new GitScmLocator(gitRepoCloneDir, List.of(gitRepoUri))
                .resolveTagInfo(Gav.of("relaxngDatatype:relaxngDatatype:20020414"));
        Assertions.assertNotNull(tag);
        Assertions.assertEquals(tag.getTag(), tag.getHash());
        Assertions.assertEquals(tag.getRepoInfo().getUri(),
                "https://github.com/java-schema-utilities/relaxng-datatype-java.git");
    }

    @Test
    void lookupScmInfoCommonsLang() {
        assertCommonsTag(new GitScmLocator(gitRepoCloneDir, List.of(gitRepoUri)));
    }

    private void assertCommonsTag(GitScmLocator locator) {
        TagInfo tag = locator.resolveTagInfo(Gav.of("commons-lang:commons-lang:2.5"));
        Assertions.assertNotNull(tag);
        Assertions.assertEquals(tag.getTag(), "LANG_2_5");
        Assertions.assertNotEquals(tag.getTag(), tag.getHash());
        Assertions.assertEquals(tag.getRepoInfo().getUri(), "https://github.com/apache/commons-lang.git");
    }

    @Test
    void reuseWorkingCopy() {
        final Path gitCloneDir = Path.of("target/GitScmLocatorTest/lookupScmInfoCommonsLang-" + UUID.randomUUID());
        assertThat(gitCloneDir).doesNotExist();

        final String repoUrl = gitRepoUri;
        {
            long t1 = System.currentTimeMillis();
            final GitScmLocator locator = new GitScmLocator(gitCloneDir, List.of(repoUrl));
            assertCommonsTag(locator);

            log.infof("Lookup time with clonig: %d ms", (System.currentTimeMillis() - t1));

            assertThat(gitCloneDir).exists();
            assertThat(gitCloneDir.resolve(
                    RecipeGroupManager.uriToFileName(repoUrl) + "/.git")).exists();
        }

        // reuse the existing repo
        {
            long t1 = System.currentTimeMillis();
            final GitScmLocator locator = new GitScmLocator(gitCloneDir, List.of(repoUrl));
            assertCommonsTag(locator);
            log.infof("Lookup time with fetch & reset: %d ms", (System.currentTimeMillis() - t1));
        }
    }

    //test tag mapping heuristics
    @Test
    void runTagHeuristic() {
        runPassingTest("1.0", "1.0", "1.0", "1.0.Alpha1", "1.0.1");
        runPassingTest("1.0", "v1.0", "v1.0", "1.0.Alpha1", "1.0.1");
        runPassingTest("1.0", "v1.0", "v1.0", "1.0.0", "1.0.1");
        runPassingTest("1.0", "v1.0", "v1.0", "1.0.0", "1.0.1");
        runPassingTest("4.9.3", "4.9.3", "antlr4-master-4.9.3", "4.9.3-rc1", "4.9.3");
        runPassingTest("1.0.Final", "1.0", "1.0", "1.1");
        runPassingTest("1.0.Final", "1.0", "1.0", "1.0.a1");
        runFailingTest("1.0", "1.0.Beta1", "1.0.Alpha1");
        runFailingTest("1.0", "1.0.Final", "1.0.Alpha1");
    }

    @Test
    void uriToFileName() {
        assertThat(RecipeGroupManager.uriToFileName("https://github.com/path/to/report.pdf?download=1#section"))
                .isEqualTo("github.com-path-to-report.pdf-download-1-section");
        assertThat(RecipeGroupManager.uriToFileName("https://github.com/org/repo.git")).isEqualTo("github.com-org-repo");
        assertThat(RecipeGroupManager.uriToFileName("file:///C:/Program Files/Some App/app.exe"))
                .isEqualTo("C-Program-Files-Some-App-app.exe");
        assertThat(RecipeGroupManager.uriToFileName("C:\\Program Files\\Some App\\app.exe"))
                .isEqualTo("C-Program-Files-Some-App-app.exe");
        assertThat(RecipeGroupManager.uriToFileName("git+ssh://git@github.com:owner/repo.git"))
                .isEqualTo("github.com-owner-repo");
        assertThat(RecipeGroupManager.uriToFileName("https://example.com/trailing-dot.")).isEqualTo("example.com-trailing-dot");
        assertThat(RecipeGroupManager.uriToFileName("git@github.com:quarkusio/quarkus.git"))
                .isEqualTo("github.com-quarkusio-quarkus");
    }

    void runPassingTest(String version, String expected, String... tags) {
        Map<String, String> tagMap = new HashMap<>();
        Arrays.stream(tags).forEach(a -> tagMap.put(a, ""));
        Assertions.assertEquals(expected, GitScmLocator.runTagHeuristic(version, tagMap));
    }

    void runFailingTest(String version, String... tags) {
        Map<String, String> tagMap = new HashMap<>();
        Arrays.stream(tags).forEach(a -> tagMap.put(a, ""));
        Assertions.assertThrows(RuntimeException.class, () -> {
            GitScmLocator.runTagHeuristic(version, tagMap);
        });
    }
}
