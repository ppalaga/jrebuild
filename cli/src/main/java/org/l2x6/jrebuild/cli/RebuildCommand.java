/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.Vertx;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.jboss.logging.Logger;
import org.l2x6.jrebuild.core.build.BuildReport;
import org.l2x6.jrebuild.core.build.BuildRequest;
import org.l2x6.jrebuild.core.build.BuildRequestAlternatives;
import org.l2x6.jrebuild.core.build.Reproducibility;
import org.l2x6.jrebuild.core.build.service.BuildReportStorage;
import org.l2x6.jrebuild.core.build.service.LocalRebuildService;
import org.l2x6.jrebuild.core.build.service.LocalToolService;
import org.l2x6.jrebuild.core.build.service.ResourceMatchService;
import org.l2x6.jrebuild.core.jackson.Mapper;
import org.l2x6.jrebuild.core.maven.DeployDirectoriesLayout;
import org.l2x6.jrebuild.core.maven.ReferenceMavenRepository;
import org.l2x6.jrebuild.core.scm.CloneDirectoriesLayout;
import picocli.CommandLine;

@CommandLine.Command(name = "rebuild", description = """
        Rebuild the given BuildRequests.
        """)
public class RebuildCommand implements Runnable {
    private static final Logger log = Logger.getLogger(RebuildCommand.class);

    @CommandLine.Mixin
    CacheOptions cacheOptions;

    @CommandLine.Mixin
    M2Options m2Options;

    @CommandLine.Option(names = {
            "--reproducibility", "-r" }, description = """
                    One of PERFECT, SUFFICIENT or BUILDABLE - the desired reproducibility level.
                    Once this reproducibility level is reached for some BuildRequest,
                    any attempts to build subsequent alternative BuildRequests are given up.
                    """, defaultValue = "PERFECT")
    Reproducibility reproducibility;

    @CommandLine.Option(names = {
            "--force", "-f" }, description = """
                    Force rebuilding all BuildRequests even if those exact requests were built in the past already.
                    """, defaultValue = "true", fallbackValue = "true")
    boolean force;

    @CommandLine.Option(names = {
            "--build-reports-dir" }, description = """
                    Root directory of a build report storage where to lookup past builds or store new build reports.
                    If not specified, the storage directory is auto-detected from the current working directory.
                    """, defaultValue = ".")
    Path buildReportsDir;

    @CommandLine.Parameters(
            description = """
                    One or more paths to YAML files storing BuildRequests to rebuild
                    """,
            arity = "1..2147483647")
    List<Path> buildRequestPaths;

    @Inject
    Vertx vertx;

    public RebuildCommand() {
    }

    @Override
    public void run() {
        final Path absM2Repo = cacheOptions.resolveHome(m2Options.m2Repo);
        final Path absBuildReportsDir = cacheOptions.resolveHome(buildReportsDir);
        if (!Files.exists(absBuildReportsDir)) {
            try {
                Files.createDirectories(absBuildReportsDir);
            } catch (IOException e) {
                throw new RuntimeException("Could not create " + absBuildReportsDir, e);
            }
        }
        final Path deployRootDirectory = Path.of(System.getProperty("java.io.tmpdir")).resolve("jrebuild-deploy");
        CloneDirectoriesLayout cloneDirectoriesLayout = new CloneDirectoriesLayout(cacheOptions.clonesDir());
        DeployDirectoriesLayout deployDirectoriesLayout = new DeployDirectoriesLayout(deployRootDirectory, vertx);
        LocalToolService tools = new LocalToolService(cacheOptions.toolsCacheDir());
        ReferenceMavenRepository referenceMavenRepository = new ReferenceMavenRepository(
                m2Options.refRepoUri,
                absM2Repo,
                cacheOptions.localReferenceMavenRepositoryDir(),
                vertx);
        ResourceMatchService matchService = ResourceMatchService.createMain();
        BuildReportStorage buildReportStorage = BuildReportStorage.local(
                vertx.fileSystem(),
                absBuildReportsDir);

        LocalRebuildService rebuildService = new LocalRebuildService(
                vertx,
                cloneDirectoriesLayout,
                deployDirectoriesLayout,
                tools,
                referenceMavenRepository,
                matchService,
                buildReportStorage);

        for (Path p : buildRequestPaths) {
            String content;
            try {
                content = Files.readString(p);
            } catch (IOException e) {
                throw new RuntimeException("Could not read " + p, e);
            }
            if (BuildRequestAlternatives.canParse(content)) {
                BuildRequestAlternatives alts;
                try {
                    alts = Mapper.instance().readValue(content, BuildRequestAlternatives.class);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException("Could not parse " + p, e);
                }
                for (BuildRequest buildRequest : alts) {
                    BuildReport result = rebuild(force, buildRequest, rebuildService, absBuildReportsDir);
                    if (result.reproducibility().overall().isBetterOrSame(reproducibility)) {
                        break;
                    }
                }
            } else {
                BuildRequest buildRequest = null;
                try {
                    buildRequest = Mapper.instance().readValue(content, BuildRequest.class);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException("Could not parse " + p, e);
                }
                rebuild(force, buildRequest, rebuildService, absBuildReportsDir);
            }
        }
    }

    BuildReport rebuild(boolean force, BuildRequest buildRequest, LocalRebuildService rebuildService, Path reportStorageDir) {
        log.infof("Rebuilding %s ...", buildRequest.buildGroup());
        Uni<BuildReport> resultUni = force
                ? rebuildService.build(buildRequest)
                : rebuildService.ensureBuilt(
                        buildRequest,
                        reproducibility);
        BuildReport result = resultUni.await().indefinitely();
        log.infof("Stored build report in %s", BuildReportStorage.FilesystemBuildReportStorage.path(reportStorageDir, result));
        log.infof("Rebuilt\n\n%s:\n\n%s\n.", buildRequest.buildGroup(), result.reproducibility().toFormatedString("    "));
        return result;
    }

}
