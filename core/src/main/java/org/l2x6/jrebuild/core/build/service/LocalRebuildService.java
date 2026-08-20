package org.l2x6.jrebuild.core.build.service;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.vertx.mutiny.core.Vertx;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.cliassured.CliAssured;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Ref;
import org.l2x6.jrebuild.api.os.OsArch;
import org.l2x6.jrebuild.api.os.Tool;
import org.l2x6.jrebuild.api.os.Tool.InstalledTool;
import org.l2x6.jrebuild.api.scm.FqScmRef;
import org.l2x6.jrebuild.api.scm.ScmRepository;
import org.l2x6.jrebuild.common.CommonUtils;
import org.l2x6.jrebuild.common.StackTraceLessException;
import org.l2x6.jrebuild.common.git.GitUtils;
import org.l2x6.jrebuild.core.build.BuildReport;
import org.l2x6.jrebuild.core.build.BuildRequest;
import org.l2x6.jrebuild.core.build.Reproducibility;
import org.l2x6.jrebuild.core.build.Resource;
import org.l2x6.jrebuild.core.build.ResourceMatch;
import org.l2x6.jrebuild.core.build.ResourceMatchLevel;
import org.l2x6.jrebuild.core.maven.DeployDirectoriesLayout;
import org.l2x6.jrebuild.core.maven.DeployDirectoriesLayout.DeployDirectory;
import org.l2x6.jrebuild.core.maven.LocalMavenRepository;
import org.l2x6.jrebuild.core.maven.ReferenceMavenRepository;
import org.l2x6.jrebuild.core.scm.CloneDirectoriesLayout;
import org.l2x6.jrebuild.core.scm.CloneDirectoriesLayout.CloneDirectory;
import org.l2x6.pom.tuner.model.Gavtc;
import org.l2x6.pom.tuner.model.Gavtcf;
import org.l2x6.pom.tuner.model.OptionalWithDefault;

public record LocalRebuildService(
        Vertx vertx,
        CloneDirectoriesLayout cloneDirectoriesLayout,
        DeployDirectoriesLayout deployDirectoriesLayout,
        LocalToolService tools,
        ReferenceMavenRepository referenceMavenRepository,
        ResourceMatchService matchService,
        BuildReportStorage buildReportStorage) {

    public Uni<BuildReport> ensureBuilt(BuildRequest buildRequest, Reproducibility requiredReproducibility) {
        return ensureBuilt(buildRequest, requiredReproducibility, Clock.systemUTC());
    }

    Uni<BuildReport> ensureBuilt(BuildRequest buildRequest, Reproducibility requiredReproducibility, Clock clock) {
        return buildReportStorage.list(buildRequest.buildGroup().fqScmRef())
                .select().where(report -> report.reproducibility().isBetterOrSame(requiredReproducibility)
                        && report.containsAll(buildRequest.buildGroup().artifacts()))
                .collect()
                .with(Collectors.minBy(BuildReport.byBestReproducibilityAndNewestTimestamp()))
                .onItem().transformToUni(maybeReport -> {
                    if (maybeReport.isPresent()) {
                        return Uni.createFrom().item(maybeReport.get());
                    }
                    /* We have to rebuild */
                    /* Create or find the build directory */
                    return cloneDirectoriesLayout.lockDirectory(buildRequest.buildGroup().fqScmRef().repository().uri())
                            .onItem()
                            .transformToUni(cloneDir -> deployDirectoriesLayout
                                    .createDeployDirectory()
                                    .chain(deployDirectory -> build(
                                            vertx,
                                            cloneDir,
                                            deployDirectory,
                                            buildRequest,
                                            tools,
                                            matchService,
                                            referenceMavenRepository,
                                            clock)
                                            .onItem().transformToUni(buildReportStorage::store)
                                            .eventually(cloneDir::close)
                                            .eventually(deployDirectory::close)));
                });

    }

    static Uni<BuildReport> build(
            Vertx vertx,
            CloneDirectory cloneDir,
            DeployDirectory deployDirectory,
            BuildRequest buildRequest,
            LocalToolService tools,
            ResourceMatchService matchService,
            ReferenceMavenRepository referenceMavenRepository,
            Clock clock) {
        ZonedDateTime ts = ZonedDateTime.now(clock.withZone(ZoneOffset.UTC));
        final FqScmRef scmRef = buildRequest.buildGroup().fqScmRef();
        final ScmRepository repo = scmRef.repository();
        if (!repo.isGit()) {
            return Uni.createFrom()
                    .item(buildReportFailure(buildRequest, clock, ts, repo, "Cannot checkout from SCM type " + repo.type()));
        }
        OsArch currentOsArch = OsArch.current();
        if (!currentOsArch.equals(buildRequest.osArch())) {
            return Uni.createFrom().item(buildReportFailure(buildRequest, clock, ts, repo,
                    "The current OS " + currentOsArch + " does not match the requested OS " + buildRequest.osArch()));
        }

        @SuppressWarnings("unused")
        Uni<String> commitIdUni = Uni.createFrom()
                .item(() -> {
                    /* Checkout the sources */
                    String commitId = null;
                    try (Git git = GitUtils.cloneOrFetchAndReset(
                            scmRef,
                            cloneDir.cloneDirectory(),
                            1)) {
                        final Ref ref = git.getRepository().exactRef("HEAD");
                        commitId = ref.getObjectId().getName();
                    } catch (Exception e) {
                        throw new BuildReportFailure(new BuildReport(
                                buildRequest,
                                commitId,
                                Reproducibility.INVALID_SOURCE_INFO,
                                ts,
                                Duration.between(ts, ZonedDateTime.now(clock.withZone(ZoneOffset.UTC))),
                                Map.of(),
                                "Could not fetch from " + repo.uri() + "\n" + CommonUtils.stackTrace(e)));
                    }

                    /* Install the tools and prepare the PATH env var */
                    String colon = System.getProperty("path.separator");
                    StringJoiner joiner = new StringJoiner(colon);
                    Map<String, String> env = new LinkedHashMap<>();
                    for (Tool tool : buildRequest.tools()) {
                        // TODO: install the tools in parallel
                        // TODO: even in parallel with git checkout
                        InstalledTool installed = tools.install(tool);
                        installed.preparePathEnvironmentVariable(joiner::add);
                        installed.prepareEnvironmentVariables(env::put);
                    }
                    joiner.add(System.getenv("PATH"));
                    final String pathEnvVar = joiner.toString();

                    /* Run the script */
                    List<String> cmd = buildRequest.shell().command(buildRequest.osArch(), buildRequest.buildScript());
                    try {
                        CliAssured.command(cmd.get(0))
                                .args(cmd.subList(1, cmd.size()))
                                .cd(cloneDir.cloneDirectory())
                                .env("PATH", pathEnvVar)
                                .env("DEPLOYMENT_REPO", deployDirectory.deployDirectory().toUri().toString())
                                .env(env)
                                .stderrToStdout()
                                .then()
                                .stdout()
                                .log()
                                .execute()
                                .assertSuccess();
                    } catch (AssertionError e) {
                        throw new BuildReportFailure(new BuildReport(
                                buildRequest,
                                commitId,
                                Reproducibility.UNBUILDABLE,
                                ts,
                                Duration.between(ts, ZonedDateTime.now(clock.withZone(ZoneOffset.UTC))),
                                Map.of(),
                                "Could not build " + repo.uri() + "\n" + e.getMessage().trim()));
                    } catch (Throwable e) {
                        throw new BuildReportFailure(new BuildReport(
                                buildRequest,
                                commitId,
                                Reproducibility.UNBUILDABLE,
                                ts,
                                Duration.between(ts, ZonedDateTime.now(clock.withZone(ZoneOffset.UTC))),
                                Map.of(),
                                "Could not build " + repo.uri() + "\n" + CommonUtils.stackTrace(e)));
                    }
                    return commitId;
                })
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());

        Uni<BuildReport> buildReport = commitIdUni.onItem()
                .transformToUni(commitId -> {

                    /* Check if everything was deployed, report missing artifacts if needed */
                    final Multi<Gavtcf> rebuiltArtifacts = new LocalMavenRepository(deployDirectory.deployDirectory(),
                            vertx.fileSystem())
                            .gavtcfStream();

                    Uni<BuildReport> innerBuildReport = rebuiltArtifacts
                            .onItem()
                            .transformToUniAndMerge(rebuiltGavtcf -> referenceMavenRepository.resolve(rebuiltGavtcf.toGavtc())
                                    .chain(refGavtcsf -> Uni.createFrom()
                                            .item(() -> {
                                                ResourceMatch match = matchService.compare(Resource.of(refGavtcsf),
                                                        Resource.of(rebuiltGavtcf));
                                                return new ArtifactInfo(rebuiltGavtcf.toGavtc(), match);
                                            })
                                            .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())))
                            .collect().asList()
                            .onItem().transform(artifactInfos -> {

                                final Map<Gavtc, ResourceMatch> builtArtifactsMap = new TreeMap<>(
                                        Gavtc.groupFirstComparator(OptionalWithDefault.valueOrDefaultComparator()));
                                artifactInfos.forEach(ai -> builtArtifactsMap.put(ai.gavtc, ai.resourceMatch));

                                buildRequest.buildGroup().artifacts().stream()
                                        .filter(a -> !builtArtifactsMap.containsKey(a))
                                        .forEach(a -> builtArtifactsMap.put(
                                                a,
                                                ResourceMatch.of(ResourceMatchLevel.MISSING_IN_REBUILD,
                                                        a.getRepositoryPath())));
                                Reproducibility reproducibility = builtArtifactsMap.values().stream()
                                        .map(ResourceMatch::level)
                                        .sorted(Comparator.comparing(ResourceMatchLevel::ordinal))
                                        .findFirst()
                                        .orElseThrow()
                                        .reproducibility();

                                return new BuildReport(
                                        buildRequest,
                                        commitId,
                                        reproducibility,
                                        ts,
                                        Duration.between(ts, ZonedDateTime.now(clock.withZone(ZoneOffset.UTC))),
                                        builtArtifactsMap,
                                        null);

                            });

                    return innerBuildReport;

                });

        return buildReport.onFailure().recoverWithItem(e -> {
            if (e instanceof BuildReportFailure) {
                return ((BuildReportFailure) e).buildReport;
            } else {
                return buildReportFailure(buildRequest, clock, ts, repo,
                        "Could not build " + repo.uri() + "\n" + CommonUtils.stackTrace(e));
            }
        });

    }

    static BuildReport buildReportFailure(BuildRequest buildRequest, Clock clock, ZonedDateTime ts,
            final ScmRepository repo, String message) {
        return new BuildReport(
                buildRequest,
                null,
                Reproducibility.FAILED,
                ts,
                Duration.between(ts, ZonedDateTime.now(clock.withZone(ZoneOffset.UTC))),
                Map.of(),
                message);
    }

    public static record ArtifactInfo(
            Gavtc gavtc,
            ResourceMatch resourceMatch) {
    }

    static class BuildReportFailure extends StackTraceLessException {
        private static final long serialVersionUID = 1L;
        private final BuildReport buildReport;

        public BuildReportFailure(BuildReport buildReport) {
            super(null);
            this.buildReport = buildReport;
        }

    }

}
