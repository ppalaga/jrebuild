/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.cli;

import eu.maveniverse.maven.mima.context.Context;
import eu.maveniverse.maven.mima.context.ContextOverrides;
import eu.maveniverse.maven.mima.context.Runtime;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import org.jboss.logging.Logger;
import org.l2x6.jrebuild.core.dep.DependencyCollector;
import org.l2x6.jrebuild.core.dep.DependencyCollectorRequest;
import org.l2x6.jrebuild.core.dep.DependencyCollectorRequest.Builder;
import org.l2x6.jrebuild.core.dep.ManagedGavsSelector;
import org.l2x6.jrebuild.core.dep.ResolvedArtifactNode;
import org.l2x6.jrebuild.core.mima.JRebuildRuntime;
import org.l2x6.jrebuild.core.mima.internal.CachingMavenModelReader;
import org.l2x6.jrebuild.core.scm.GitRemoteScmLookup;
import org.l2x6.jrebuild.core.scm.ScmRepositoryService;
import org.l2x6.jrebuild.core.scm.ScmRepositoryService.ScmInfoNode;
import org.l2x6.jrebuild.core.tree.PrintVisitor;
import org.l2x6.jrebuild.core.tree.Visitor;
import org.l2x6.pom.tuner.model.Gav;
import org.l2x6.pom.tuner.model.GavSet;
import org.l2x6.pom.tuner.model.Gavtc;
import org.l2x6.pom.tuner.model.GavtcsPattern;
import org.l2x6.pom.tuner.model.GavtcsSet;
import picocli.CommandLine;
import picocli.CommandLine.ITypeConverter;

@CommandLine.Command(name = "analyze")
public class AnalyzeCommand implements Runnable {
    private static final Logger log = Logger.getLogger(AnalyzeCommand.class);

    @CommandLine.Option(names = { "--project-dir" }, description = "A directory containing a source tree to analyze")
    Path projectDir;

    @CommandLine.Option(names = {
            "--bom" },
            description = "BOM in format groupId:artifactId:version whose constraints should be used as top level artifacts to be built",
            converter = GavConverter.class)
    Gav bom;

    @CommandLine.Option(names = {
            "--bom-includes" },
            description = """
                    A list of patterns in format groupId[:artifactId[:version[:type[:classifier]]]] where each segment may contain one or more * wildcards.
                    These patterns are used for filtering the entries of the BOM (specified through --bom) and are added to the set of root artifacts.
                    """,
            converter = GavtcsPatternConverter.class, split = ",")
    List<GavtcsPattern> bomIncludes = List.of(GavtcsPattern.matchAll());

    @CommandLine.Option(names = {
            "--excludes" },
            description = """
                    A list of patterns in format groupId[:artifactId[:version[:type[:classifier]]]] where each segment may contain one or more * wildcards.
                    Artifacts matching any of these patterns are excluded from the set of root artifacts and if any of those artifacts is hit during
                    the analysis then the artifact is ignored and the analysis won't descend to its dependencies.
                    """,
            converter = GavtcsPatternConverter.class, split = ",")
    List<GavtcsPattern> excludes = List.of();

    @CommandLine.Option(names = {
            "--root-artifacts" },
            description = """
                    Root artifacts whose dependencies should be analyzed in format groupId:artifactId:version[:type[:classifier]].
                    Note that root artifacts can also be specified via --bom, --bom-includes (and --excludes if needed).
                    """,
            converter = GavtcConverter.class, split = ",")
    List<Gavtc> rootArtifacts = List.of();

    @CommandLine.Option(names = {
            "--include-optional-deps" },
            description = """
                    If true, all optional dependencies (both first level and transitive) of root artifacts will be processed;
                    otherwise only the first level optionals will be processed
                    """,
            defaultValue = "true", fallbackValue = "true")
    boolean includeOptionalDeps;

    @CommandLine.Option(names = {
            "--include-parents-and-imports" },
            description = "If true, process also parents and dependencyManagement imports as if they were dependencies; otherwise process only dependencies",
            defaultValue = "true", fallbackValue = "true")
    boolean includeParentsAndImports;

    @CommandLine.Option(names = {
            "--additional-boms" },
            description = """
                    A list of groupId:artifactId:version whose constraints should be enforced in addition to the main BOM specified through --bom.
                    BOMs specified via --additional-boms do not extend the universe for --bom-includes.
                    """,
            converter = GavConverter.class, split = ",")
    List<Gav> additionalBoms = List.of();

    @CommandLine.Option(names = {
            "--cut-stem" },
            description = """
                    A list of patterns in format groupId[:artifactId[:version[:type[:classifier]]]] where each segment may contain one or more * wildcards.
                    After creating the initial dependency trees of all root artiafcts, these patterns are used for removing some
                    (possibly empty) rooted part (i.e. stem) of those dependecy trees before searching for build metadata.
                    This is typically useful when you want to analyze only dependencies of some project, but not the project itself.
                    In such a situation, you would use --exclude-stem to exclude the artifacts belonging to that project.
                    """,
            converter = GavSetConverter.class)
    GavSet stem = GavSet.excludeAll();

    @CommandLine.Option(names = {
            "--buildspec-clone-dir" },
            description = "A directory where to clone remote Domino and Reproducible Central recipes",
            defaultValue = "~/.m2/buildspec")
    Path dominoCloneDir;

    @CommandLine.Option(names = {
            "--domino-recipes-urls" }, description = "A list of Git URLs hosting Domino build recipes", split = ",")
    Set<String> dominoRecipeUrls = Set.of();

    @CommandLine.Option(names = {
            "--reproducible-central-urls" },
            description = "A list of Git URLs hosting Reproducible Central buildspecs, such as https://github.com/jvm-repo-rebuild/reproducible-central.git",
            split = ",")
    Set<String> reproducibleCentralUrls = Set.of();

    @CommandLine.Option(names = {
            "--ls-remotes-older-than" }, description = """
                    A timestamp in 2025-12-01T10:15:30Z format determining how fresh the entries in ls-remotes-cache must be.
                    You should typically set this to the release date of the root artifacts you are analyzing.
                    E.g. if you are analyzing artigfacts from a project that was released on 2025-12-01T10:15:30Z,
                    then it is fine to set --ls-remotes-older-than=2025-12-01T10:15:30Z
                    because it should be fine to assume that all its dependencies were tagged before that date.
                    If not specified, then it is set to first the execution time on the given day.
                    Use --ls-remotes-older-than=now to force refreshing the entries in ls-remotes-cache.
                    """)
    String rawMinRetievalTime;
    Instant minRetievalTime;

    @CommandLine.Option(names = {
            "--ls-remotes-cache" }, description = """
                    A file where the tag name -> sha1 mappings are cached for remote SCM repositories
                    """, defaultValue = "~/.cache/jrebuild/ls-remotes-cache.txt")
    Path lsRemotesCache;

    public AnalyzeCommand() {
    }

    @Override
    public void run() {

        final Path userHome = Path.of(System.getProperty("user.home"));
        dominoCloneDir = resolveHome(userHome, dominoCloneDir);
        lsRemotesCache = resolveHome(userHome, lsRemotesCache);

        final Path cacheDir = userHome.resolve(".cache/jrebuild");
        if (rawMinRetievalTime == null) {
            minRetievalTime = defaultMinRetrievalTime(cacheDir);
        } else if ("now".equals(rawMinRetievalTime)) {
            minRetievalTime = Instant.now();
        } else {
            minRetievalTime = Instant.parse(rawMinRetievalTime);
        }

        Runtime runtime = JRebuildRuntime.getInstance();
        ContextOverrides.Builder overrides = ContextOverrides.create();
        try (Context context = runtime.create(overrides.build())) {

            final Builder builder = DependencyCollectorRequest.builder()
                    .projectDirectory(projectDir)
                    .includeOptionalDependencies(includeOptionalDeps)
                    .includeParentsAndImports(includeParentsAndImports)
                    .additionalBoms(additionalBoms)
                    .rootArtifacts(rootArtifacts);
            if (bom != null) {
                final GavtcsSet gavtcsSet = GavtcsSet.builder()
                        .includePatterns(bomIncludes)
                        .excludePatterns(excludes)
                        .build();
                final Set<Gavtc> bomRootArtifacts = new ManagedGavsSelector(
                        context.lookup().lookup(CachingMavenModelReader.class).get()::readEffectiveModel)
                        .select(bom, gavtcsSet);
                builder
                        .rootBom(bom)
                        .rootArtifacts(bomRootArtifacts)
                        .excludes(excludes);
            }

            final DependencyCollectorRequest re = builder.build();

            if (re.rootArtifacts().isEmpty()) {
                throw new IllegalStateException(
                        "Specify some root artifacts using (a) --root-artifacts groupId[:artifactId[:version[:type[:classifier]]]][,groupId[:artifactId[:version[:type[:classifier]]]],...] or (b) using --bom groupId:artifactId:version and --bom-includes and --bom-excludes or by combining (a) and (b)");
            }

            try (GitRemoteScmLookup remoteScm = new GitRemoteScmLookup(lsRemotesCache, minRetievalTime)) {
                final ScmRepositoryService locator = ScmRepositoryService.create(
                        context.lookup().lookup(CachingMavenModelReader.class).get()::readEffectiveModel,
                        remoteScm,
                        dominoCloneDir,
                        reproducibleCentralUrls,
                        dominoRecipeUrls);

                DependencyCollector.collect(context, re)
                        .onItem().transformToMulti(resolvedArtifact -> new CutStemVisitor(stem).walk(resolvedArtifact).result())
                        .merge()
                        .onItem().transformToUniAndMerge(resolvedArtifact -> Uni.createFrom().item(() -> {
                            ScmInfoNode rootScmInfoNode = locator.newVisitor().walk(resolvedArtifact).rootNode();
                            return PrintVisitor.toString(rootScmInfoNode);
                        })
                                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())

                        )
                        .onItem().invoke(p -> log.infof("Scm Repos:\n%s", p))
                        .map(p -> null)
                        .collect().asList()
                        .await().indefinitely();
                ;
            }
        }
    }

    static Path resolveHome(Path userHome, Path path) {
        if (path.startsWith("~")) {
            return userHome.resolve(path.subpath(1, path.getNameCount()));
        }
        return path;
    }

    static Instant defaultMinRetrievalTime(Path cacheDir) {
        final Path lastRunProperties = cacheDir.resolve("last-run.properties");
        final Properties props = new Properties();
        boolean exists = Files.exists(lastRunProperties);
        if (exists) {
            try (InputStream in = Files.newInputStream(lastRunProperties)) {
                props.load(in);
            } catch (IOException e) {
                throw new RuntimeException("Could not read " + lastRunProperties);
            }
        }
        final String rawMinimalRetievalTime = (String) props.getProperty("refresh-remotes-older-than");
        if (rawMinimalRetievalTime != null) {
            final Instant result = Instant.parse(rawMinimalRetievalTime);
            Instant startOfTheDay = LocalDate.now()
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant();
            if (result.isBefore(startOfTheDay)) {
                return storeLastRun(lastRunProperties, props);
            } else {
                log.infof(
                        "Loaded default refresh-remotes-older-than = %s from %s; if you may want to override the value using the option --refresh-remotes-older-than",
                        result, lastRunProperties);
                return result;
            }
        }
        return storeLastRun(lastRunProperties, props);
    }

    static Instant storeLastRun(Path lastRunProperties, Properties props) {
        final Instant result = Instant.now();
        props.setProperty("refresh-remotes-older-than", result.toString());
        if (!Files.exists(lastRunProperties.getParent())) {
            try {
                Files.createDirectories(lastRunProperties.getParent());
            } catch (IOException e) {
                throw new RuntimeException("Could not create " + lastRunProperties.getParent());
            }
        }
        try (OutputStream out = Files.newOutputStream(lastRunProperties)) {
            props.store(out, "");
        } catch (IOException e) {
            throw new RuntimeException("Could not write " + lastRunProperties);
        }
        log.infof(
                "Setting refresh-remotes-older-than = %s for today and storing it in %s; if you may want to override the value using the option --refresh-remotes-older-than",
                result, lastRunProperties);
        return result;
    }

    static class CutStemVisitor implements Visitor<ResolvedArtifactNode, CutStemVisitor> {

        private final Set<ResolvedArtifactNode> stemComplement = new LinkedHashSet<>();
        private final GavSet stem;

        public CutStemVisitor(GavSet stem) {
            super();
            this.stem = stem;
        }

        public Multi<ResolvedArtifactNode> result() {
            return Multi.createFrom().iterable(stemComplement);
        }

        @Override
        public boolean enter(ResolvedArtifactNode node) {
            if (stem.contains(node.gavtc().toGav())) {
                return true;
            } else {
                stemComplement.add(node);
                return false;
            }
        }

        @Override
        public boolean leave(ResolvedArtifactNode node) {
            return true;
        }

    }

    static class GavConverter implements ITypeConverter<Gav> {
        public Gav convert(String value) throws Exception {
            return Gav.of(value);
        }
    }

    static class GavtcConverter implements ITypeConverter<Gavtc> {
        public Gavtc convert(String value) throws Exception {
            return Gavtc.of(value);
        }
    }

    static class GavtcsPatternConverter implements ITypeConverter<GavtcsPattern> {
        public GavtcsPattern convert(String value) throws Exception {
            return GavtcsPattern.of(value);
        }
    }

    static class GavSetConverter implements ITypeConverter<GavSet> {
        public GavSet convert(String value) throws Exception {
            return GavSet.builder().defaultResult(GavSet.excludeAll()).includes(value).build();
        }
    }

}
