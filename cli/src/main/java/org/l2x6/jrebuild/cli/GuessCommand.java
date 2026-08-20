/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.cli;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.Vertx;
import jakarta.inject.Inject;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.jboss.logging.Logger;
import org.l2x6.jrebuild.api.os.Tool;
import org.l2x6.jrebuild.api.scm.FqScmRef;
import org.l2x6.jrebuild.core.build.BuildGroup;
import org.l2x6.jrebuild.core.build.SourceRootDirectories;
import org.l2x6.jrebuild.core.build.service.FindReferenceArtifactsService;
import org.l2x6.jrebuild.core.maven.ReferenceMavenRepository;
import org.l2x6.jrebuild.core.scm.CloneDirectoriesLayout;
import picocli.CommandLine;
import picocli.CommandLine.Mixin;

@CommandLine.Command(name = "guess")
public class GuessCommand implements Runnable {
    private static final Logger log = Logger.getLogger(GuessCommand.class);

    @Mixin
    CacheOptions cacheOptions;

    @CommandLine.Option(names = {
            "--m2-repo" }, description = """
                    Root directory of local Maven repository
                    """, defaultValue = "~/.m2/repository")
    Path m2Repo;

    @CommandLine.Option(names = {
            "--ref-repo" }, description = """
                    URI of the Maven repository where to look for reference artifacts. Defaults to Maven Central.
                    """, defaultValue = "https://repo1.maven.org/maven2")
    String refRepoUri;

    @CommandLine.Option(names = {
            "--scm-ref" },
            description = "A fully qualified SCM reference for which the build request should be guessed. The format is [<scm-repo-type>:]<scm-repo>#<tag>. Default <scm-repo-type> is git",
            defaultValue = "~/.m2/buildspec")
    String rawScmRef;

    @CommandLine.Option(names = {
            "--source-roots" }, description = """
                    A list of subdirectories in the source tree of the analyzed repository where the build should start.
                    For Maven projects, these would be directories with root pom.xml files.
                    Defaults to root directory of the source repository.
                    """, split = ",")
    Set<Path> sourceRoots = Set.of();

    @Inject
    Vertx vertx;

    public GuessCommand() {
    }

    @Override
    public void run() {
        final Path absM2Repo = cacheOptions.resolveHome(m2Repo);
        FqScmRef scmRef = FqScmRef.of(rawScmRef);

        CloneDirectoriesLayout cloneDirs = new CloneDirectoriesLayout(cacheOptions.cacheDir().resolve("clones"));
        ReferenceMavenRepository referenceMavenRepository = new ReferenceMavenRepository(refRepoUri,
                cacheOptions.cacheDir().resolve("ref-m2-repo"), absM2Repo, vertx);
        FindReferenceArtifactsService findService = new FindReferenceArtifactsService(cloneDirs, referenceMavenRepository);

        Uni<BuildGroup<FqScmRef>> bg = findService.findPublishedArtifacts(scmRef, SourceRootDirectories.roots(sourceRoots));

        List<Tool> tools = List.of(new Tool("sdkman", "java", "11.0.25-tem"));
        String script = "./mvnw clean deploy -Prelease -ntp -DskipTests -Dgpg.skip -DskipPublishing=true deploy:deploy -DaltDeploymentRepository=local::${DEPLOYMENT_REPO}";

    }

}
