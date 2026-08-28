/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.cli;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.Vertx;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.jboss.logging.Logger;
import org.l2x6.jrebuild.api.scm.FqScmRef;
import org.l2x6.jrebuild.core.build.BuildRequestAlternatives;
import org.l2x6.jrebuild.core.build.service.BuildToolVersionsService;
import org.l2x6.jrebuild.core.build.service.FindReferenceArtifactsService;
import org.l2x6.jrebuild.core.build.service.FoojayDiscoService;
import org.l2x6.jrebuild.core.build.service.GuessBuildRequestService;
import org.l2x6.jrebuild.core.jackson.Mapper;
import org.l2x6.jrebuild.core.maven.ReferenceMavenRepository;
import org.l2x6.jrebuild.core.scm.CloneDirectoriesLayout;
import picocli.CommandLine;
import picocli.CommandLine.Mixin;

@CommandLine.Command(name = "guess")
public class GuessCommand implements Runnable {
    private static final Logger log = Logger.getLogger(GuessCommand.class);

    @Mixin
    CacheOptions cacheOptions;

    @Mixin
    M2Options m2Options;

    @CommandLine.Option(names = {
            "--output", "-o" },
            description = """
                    A file path where to write the resulting array of guessed BuildRequestAlternatives. Use - to write to STDOUT.
                    """,
            defaultValue = "-")
    String out;

    @CommandLine.Option(names = { "--force-output-array" },
            description = """
                    If present, a YAML array will be written to the output, even if only one SCM reference was specified as an input;
                    otherwise a YAML object will be written or single SCM reference and a YAML array will be written for multiple input SCM references
                    """,
            defaultValue = "false", fallbackValue = "true")
    boolean forceOutputArray;

    @CommandLine.Parameters(
            description = "One or more qualified SCM references for which the build requests should be guessed. The format is [<scm-repo-type>:]<scm-repo>#<tag>. Default <scm-repo-type> is git",
            arity = "1..2147483647")
    List<String> rawScmRefs;

    @Inject
    Vertx vertx;

    public GuessCommand() {
    }

    @Override
    public void run() {
        final Path absM2Repo = cacheOptions.resolveHome(m2Options.m2Repo);

        CloneDirectoriesLayout cloneDirs = new CloneDirectoriesLayout(cacheOptions.clonesDir());
        ReferenceMavenRepository referenceMavenRepository = new ReferenceMavenRepository(
                m2Options.refRepoUri,
                absM2Repo,
                cacheOptions.localReferenceMavenRepositoryDir(),
                vertx);
        FindReferenceArtifactsService findService = new FindReferenceArtifactsService(cloneDirs, referenceMavenRepository);

        BuildToolVersionsService buildToolVersionsService = new BuildToolVersionsService(vertx);
        FoojayDiscoService foojayDiscoService = new FoojayDiscoService();
        GuessBuildRequestService guessBuildRequestService = new GuessBuildRequestService(
                vertx.fileSystem(),
                cloneDirs,
                referenceMavenRepository,
                buildToolVersionsService,
                findService,
                foojayDiscoService);

        List<Uni<BuildRequestAlternatives>> resultUnis = rawScmRefs.stream()
                .map(FqScmRef::of)
                .map(guessBuildRequestService::guess)
                .toList();

        Uni.join().all(resultUnis).andCollectFailures()
                .chain(results -> {
                    try {
                        OutputStream outStream = "-".equals(out) ? System.out : Files.newOutputStream(Path.of(out));
                        try {
                            if (resultUnis.size() == 1 && !forceOutputArray) {
                                Mapper.instance().writeValue(outStream, results.get(0));
                            } else {
                                Mapper.instance().writeValue(outStream, results);
                            }
                        } finally {
                            if (!"-".equals(out)) {
                                outStream.close();
                            }
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    return Uni.createFrom().voidItem();
                })
                .await().indefinitely();

    }

}
