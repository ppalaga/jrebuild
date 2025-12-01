/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.cli;

import eu.maveniverse.maven.mima.context.Context;
import eu.maveniverse.maven.mima.context.ContextOverrides;
import eu.maveniverse.maven.mima.context.Runtime;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import org.jboss.logging.Logger;
import org.l2x6.jrebuild.core.dep.DependencyCollector;
import org.l2x6.jrebuild.core.dep.DependencyCollectorRequest;
import org.l2x6.jrebuild.core.dep.DependencyCollectorRequest.Builder;
import org.l2x6.jrebuild.core.dep.ManagedGavsSelector;
import org.l2x6.jrebuild.core.mima.JRebuildRuntime;
import org.l2x6.jrebuild.core.mima.internal.CachingMavenModelReader;
import org.l2x6.jrebuild.core.scm.GitRemoteScmLookup;
import org.l2x6.jrebuild.core.scm.ScmRepositoryService;
import org.l2x6.jrebuild.core.scm.ScmRepositoryService.ScmInfoNode;
import org.l2x6.jrebuild.core.tree.PrintVisitor;
import org.l2x6.pom.tuner.model.Gav;
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
            "--bom" }, description = "BOM in format groupId:artifactId:version whose constraints should be used as top level artifacts to be built", converter = GavConverter.class)
    Gav bom;

    @CommandLine.Option(names = {
            "--bom-includes" }, description = "A list of patterns in format groupId[:artifactId[:version[:type[:classifier]]]] where each segment may contain one or more * wildcards. These patterns are used for filtering the entries of the BOM (specified through --bom) and are added to the set of root artifacts", converter = GavtcsPatternConverter.class, split = ",")
    List<GavtcsPattern> bomIncludes = List.of(GavtcsPattern.matchAll());

    @CommandLine.Option(names = {
            "--excludes" }, description = "A list of patterns in format groupId[:artifactId[:version[:type[:classifier]]]] where each segment may contain one or more * wildcards. These patterns are used ", converter = GavtcsPatternConverter.class, split = ",")
    List<GavtcsPattern> excludes = List.of();

    @CommandLine.Option(names = {
            "--root-artifacts" }, description = "Root artifacts whose dependencies should be processed", converter = GavtcConverter.class, split = ",")
    List<Gavtc> rootArtifacts = List.of();

    @CommandLine.Option(names = {
            "--include-optional-deps" }, description = "If true, all optional dependencies (both first level and transitive) of root artifacts will be processed; otherwise only the first level optionals will be processed", defaultValue = "true", fallbackValue = "true")
    boolean includeOptionalDeps;

    @CommandLine.Option(names = {
            "--include-parents-and-imports" }, description = "If true, process also parents and dependencyManagement imports as if they were dependencies; otherwise process only dependencies", defaultValue = "true", fallbackValue = "true")
    boolean includeParentsAndImports;

    @CommandLine.Option(names = {
            "--additional-boms" }, description = "A list of groupId:artifactId:version whose constraints should enforced in addition to the main BOM specified through --bom", converter = GavConverter.class, split = ",")
    List<Gav> additionalBoms = List.of();

    @CommandLine.Option(names = {
            "--domino-recipes-clone-dir" }, description = "A directory where to clone remote Domino recipes", defaultValue = "~/.m2/domino-recipes")
    Path dominoCloneDir;

    @CommandLine.Option(names = {
            "--domino-recipes-urls" }, description = "A list of Git URLs hosting Domino build recipes", split = ",")
    List<String> dominoRecipeUrls = List.of();

    @CommandLine.Option(names = {
            "--refresh-remotes-older-than" }, description = "A timestamp ")
    Instant minRetievalTime;

    public AnalyzeCommand() {
    }

    @Override
    public void run() {

        final Path userHome = Path.of(System.getProperty("user.home"));
        if (dominoCloneDir.startsWith("~")) {
            dominoCloneDir = userHome
                    .resolve(dominoCloneDir.subpath(1, dominoCloneDir.getNameCount()));
        }
        final Path cacheDir = userHome.resolve(".cache/jrebuild");
        if (minRetievalTime == null) {
            minRetievalTime = defaultMinRetrievalTime(cacheDir);
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

            final ScmRepositoryService locator = ScmRepositoryService.create(
                    context.lookup().lookup(CachingMavenModelReader.class).get()::readEffectiveModel,
                    new GitRemoteScmLookup(dominoCloneDir, minRetievalTime),
                    dominoCloneDir,
                    dominoRecipeUrls);

            DependencyCollector.collect(context, re)
                    .map(resolvedArtifact -> {
                        ScmInfoNode rootScmInfoNode = locator.newVisitor().walk(resolvedArtifact).rootNode();
                        return PrintVisitor.toString(rootScmInfoNode);
                    })
                    .forEach(p -> log.infof("Scm Repos:\n%s", p));
        }
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

}
