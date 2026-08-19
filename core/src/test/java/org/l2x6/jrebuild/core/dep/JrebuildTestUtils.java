/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild
 *                                 project contributors as indicated by the @author tags
 *                                 SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.dep;

import eu.maveniverse.maven.mima.context.ContextOverrides;
import eu.maveniverse.maven.mima.context.ContextOverrides.AddRepositoriesOp;
import eu.maveniverse.maven.mima.context.ContextOverrides.Builder;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.UUID;
import org.cliassured.CommandSpec;
import org.cliassured.maven.Maven;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;

public class JrebuildTestUtils {

    static final Path deploymentRepo = Path.of("target/repo").toAbsolutePath().normalize();
    static final Path projectsSrc = Path.of("src/test/projects").toAbsolutePath().normalize();
    static final Path projectsDest = Path.of("target/projects").toAbsolutePath().normalize();

    public static Builder testRepo() {
        RemoteRepository localRepo = new RemoteRepository.Builder(
                "local-test",
                "default",
                deploymentRepo.toUri().toString())
                .setSnapshotPolicy(new RepositoryPolicy(true, "never", "ignore"))
                .build();
        return ContextOverrides.create()
                .addRepositoriesOp(AddRepositoriesOp.REPLACE)
                .repositories(List.of(localRepo))
                .withLocalRepositoryOverride(Path.of("target/local-maven-repo-" + UUID.randomUUID()));
    }

    public static void installTestProject() {
        Path testProjectSrcDir = projectsSrc.resolve("test-project");
        Path testProjectDir = projectsDest.resolve("test-project");

        if (!Files.isDirectory(testProjectDir)) {
            try {
                Files.walkFileTree(testProjectSrcDir, new SimpleFileVisitor<>() {

                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                            throws IOException {
                        Path targetDir = testProjectDir.resolve(testProjectSrcDir.relativize(dir));
                        if (!Files.exists(targetDir)) {
                            Files.createDirectories(targetDir);
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Path targetFile = testProjectDir.resolve(testProjectSrcDir.relativize(file));
                        Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }

            try {
                Files.createDirectories(deploymentRepo);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }

            CommandSpec mvn = Maven.fromMvnw().installIfNeeded().mvn();

            mvn
                    .args("deploy", "-DaltDeploymentRepository=local::" + deploymentRepo.toUri().toString())
                    .cd(testProjectDir)
                    .then()
                    .stdout()
                    .log()
                    .stderr()
                    .log()
                    .doesNotHaveLinesContainingCaseInsensitive("error")
                    .execute()
                    .assertSuccess();

            mvn
                    .args("org.codehaus.mojo:versions-maven-plugin:2.19.1:set", "-DnewVersion=0.0.1-SNAPSHOT")
                    .cd(testProjectDir)
                    .then()
                    .stdout()
                    .log()
                    .stderr()
                    .log()
                    .doesNotHaveLinesContainingCaseInsensitive("error")
                    .execute()
                    .assertSuccess();
            mvn
                    .args("org.codehaus.mojo:versions-maven-plugin:2.19.1:set", "-DnewVersion=0.0.1-SNAPSHOT")
                    .cd(testProjectDir.resolve("transitive"))
                    .then()
                    .stdout()
                    .log()
                    .stderr()
                    .log()
                    .doesNotHaveLinesContainingCaseInsensitive("error")
                    .execute()
                    .assertSuccess();

            mvn
                    .args("deploy", "-DaltDeploymentRepository=local::" + deploymentRepo.toUri().toString())
                    .cd(testProjectDir.resolve("external"))
                    .then()
                    .stdout()
                    .log()
                    .stderr()
                    .log()
                    .doesNotHaveLinesContainingCaseInsensitive("error")
                    .execute()
                    .assertSuccess();

        }
    }

}
