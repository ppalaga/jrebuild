/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild
 *                                 project contributors as indicated by the @author tags
 *                                 SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.cli;

import eu.maveniverse.maven.mima.context.Context;
import java.util.List;
import java.util.Set;
import org.l2x6.jrebuild.core.dep.DependencyCollectorRequest;
import org.l2x6.jrebuild.core.dep.DependencyCollectorRequest.Builder;
import org.l2x6.jrebuild.core.dep.ManagedGavsSelector;
import org.l2x6.jrebuild.core.mima.internal.CachingMavenModelReader;
import org.l2x6.pom.tuner.model.Gav;
import org.l2x6.pom.tuner.model.GavSet;
import org.l2x6.pom.tuner.model.Gavtc;
import org.l2x6.pom.tuner.model.GavtcsPattern;
import org.l2x6.pom.tuner.model.GavtcsSet;
import picocli.CommandLine;
import picocli.CommandLine.ITypeConverter;

public class RootArtifactsOptions extends ProjectDirOptions {
    @CommandLine.Option(names = {
            "--bom" },
            description = "BOM in format groupId:artifactId:version whose constraints should be used as top level artifacts to be built",
            converter = GavConverter.class)
    protected Gav bom;

    @CommandLine.Option(names = {
            "--bom-includes" },
            description = """
                    A list of patterns in format groupId[:artifactId[:version[:type[:classifier]]]] where each segment may contain one or more * wildcards.
                    These patterns are used for filtering the entries of the BOM (specified through --bom) and are added to the set of root artifacts.
                    """,
            converter = GavtcsPatternConverter.class, split = ",")
    protected List<GavtcsPattern> bomIncludes = List.of(GavtcsPattern.matchAll());

    @CommandLine.Option(names = {
            "--excludes" },
            description = """
                    A list of patterns in format groupId[:artifactId[:version[:type[:classifier]]]] where each segment may contain one or more * wildcards.
                    Artifacts matching any of these patterns are excluded from the set of root artifacts and if any of those artifacts is hit during
                    the analysis then the artifact is ignored and the analysis won't descend to its dependencies.
                    """,
            converter = GavtcsPatternConverter.class, split = ",")
    protected List<GavtcsPattern> excludes = List.of();

    @CommandLine.Option(names = {
            "--root-artifacts" },
            description = """
                    Root artifacts whose dependencies should be analyzed in format groupId:artifactId:version[:type[:classifier]].
                    Note that root artifacts can also be specified via --bom, --bom-includes (and --excludes if needed).
                    """,
            converter = GavtcConverter.class, split = ",")
    protected List<Gavtc> rootArtifacts = List.of();

    @CommandLine.Option(names = {
            "--include-optional-deps" }, description = """
                    If true, all optional dependencies (both first level and transitive) of root artifacts will be processed;
                    otherwise only the first level optionals will be processed
                    """, defaultValue = "true", fallbackValue = "true")
    protected boolean includeOptionalDeps;

    @CommandLine.Option(names = {
            "--include-parents-and-imports" },
            description = "If true, process also parents and dependencyManagement imports as if they were dependencies; otherwise process only dependencies",
            defaultValue = "true", fallbackValue = "true")
    protected boolean includeParentsAndImports;

    @CommandLine.Option(names = {
            "--additional-boms" },
            description = """
                    A list of groupId:artifactId:version whose constraints should be enforced in addition to the main BOM specified through --bom.
                    BOMs specified via --additional-boms do not extend the universe for --bom-includes.
                    """,
            converter = GavConverter.class, split = ",")
    protected List<Gav> additionalBoms = List.of();

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
    protected GavSet stem = GavSet.excludeAll();

    protected DependencyCollectorRequest dependencyCollectorRequest(Context context) {
        final Builder builder = DependencyCollectorRequest.builder()
                .projectDirectory(projectDir())
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
        return re;
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
