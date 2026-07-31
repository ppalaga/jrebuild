package org.l2x6.jrebuild.core.build.service;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.vertx.core.impl.ConcurrentHashSet;
import io.vertx.mutiny.core.file.FileSystem;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.AbstractMap;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.DepthWalk.RevWalk;
import org.l2x6.jrebuild.api.os.Arch;
import org.l2x6.jrebuild.api.os.Os;
import org.l2x6.jrebuild.api.os.Shell;
import org.l2x6.jrebuild.api.os.Tool;
import org.l2x6.jrebuild.api.scm.FqScmRef;
import org.l2x6.jrebuild.api.util.ComparableVersion;
import org.l2x6.jrebuild.api.util.JrebuildUtils;
import org.l2x6.jrebuild.common.StackTraceLessException;
import org.l2x6.jrebuild.common.git.GitUtils;
import org.l2x6.jrebuild.core.build.BuildGroup;
import org.l2x6.jrebuild.core.build.BuildRequest;
import org.l2x6.jrebuild.core.build.BuildTool;
import org.l2x6.jrebuild.core.build.SourceRootDirectories;
import org.l2x6.jrebuild.core.build.service.LocalToolService.CliAssuredPackager;
import org.l2x6.jrebuild.core.build.service.ResourceMatchService.BaseResourceMatchService.TextResourceMatchService;
import org.l2x6.jrebuild.core.maven.MavenVersionsService;
import org.l2x6.jrebuild.core.maven.ReferenceMavenRepository;
import org.l2x6.jrebuild.core.scm.CloneDirectoriesLayout;
import org.l2x6.pom.tuner.model.Gavtcf;

public record GuessBuildRequestService(
        FileSystem fileSystem,
        CloneDirectoriesLayout cloneDirectoriesLayout,
        ReferenceMavenRepository referenceMavenRepository,
        MavenVersionsService mavenVersionsService,
        FindReferenceArtifactsService findReferenceArtifactsService,
        FoojayDiscoService foojayDiscoService) {

    public Uni<BuildRequest> guess(FqScmRef scmRef) {

        return cloneDirectoriesLayout.lockDirectory(scmRef.repository().uri())
                .chain(cloneDir -> GitUtils
                        .cloneOrFetchAndResetAsync(scmRef, cloneDir.cloneDirectory(), -1)
                        .chain(git -> analyzeSourceTree(scmRef, git)
                                .chain(buildInfo -> findReferenceArtifactsService
                                        .findPublishedArtifacts(scmRef,
                                                SourceRootDirectories.roots(buildInfo.rootPaths), cloneDir)
                                        .chain(buildGroup -> analyzeJars(buildGroup, buildInfo)
                                                .chain(v -> guessToolVersions(buildGroup, buildInfo))
                                                .chain(v -> {
                                                    Os os = buildInfo.os();
                                                    Shell shell = os.defaultShell();
                                                    Arch arch = buildInfo.arch();
                                                    Optional<BuildToolAndVersion> bt = buildInfo.buildTool();
                                                    if (bt.isEmpty()) {
                                                        throw new IllegalStateException("Could not detect build tool for " + buildGroup.fqScmRef().repository().uri());
                                                    }
                                                    BuildToolAndVersion btv = bt.get();

                                                    Optional<JavaDistroAndVersion> javaDistro = buildInfo.javaDistroAndVersion();
                                                    return detectBuildScript(btv.buildTool(), buildGroup, os, arch, shell)
                                                                    .map(buildScript -> new BuildRequest(buildGroup,
                                                                            os, arch, shell,
                                                                            tools(btv.tool(), javaDistro.tool()),
                                                                            buildScript));
                                                })))
                                .eventually(git::close))
                        .eventually(cloneDir::close));

        // find the build timeStamp in JavaDoc and set -Dproject.build.outputTimestamp=yyyy-MM-dd'T'HH:mm:ssXXX
    }

    static List<Tool> tools(Optional<Tool>... tools) {
        return Stream.of(tools)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toUnmodifiableList());
    }

    Uni<BuildInfo> analyzeSourceTree(FqScmRef scmRef, Git git) {
        ToolVersionDetector<BuildTool> buildToolDetector = ToolVersionDetector.createBuildToolDetector();
        ToolVersionDetector<String> javaDistroDetector = ToolVersionDetector.createJavaDistroDetector();
        Set<Os> oses = new ConcurrentHashSet<>();
        Path workingCopy = git.getRepository().getWorkTree().toPath();
        return detectMaven(git, workingCopy, buildToolDetector, javaDistroDetector, oses)
                .onItem().ifNull().switchTo(detectGradle(workingCopy))
                .onItem().ifNull().switchTo(detectAnt(workingCopy))
                .onItem().ifNull()
                .switchTo(Uni.createFrom().failure(new StackTraceLessException("Could not detect build tool for " + scmRef)));
    }

    Uni<BuildInfo> detectGradle(Path workingCopy) {
        return Uni.createFrom().nullItem();
    }

    Uni<BuildInfo> detectAnt(Path workingCopy) {
        return Uni.createFrom().nullItem();
    }

    Uni<BuildInfo> detectMaven(Git git, Path workingCopy, ToolVersionDetector buildToolDetector, ToolVersionDetector javaDistroDetector, Set<Os> oses) {
        return findRootDirectories(workingCopy, dir -> fileSystem.exists(dir.resolve("pom.xml").toString()))
                .chain(rootDirs -> {
                    if (rootDirs.isEmpty()) {
                        return Uni.createFrom().nullItem();
                    }
                    // Check whether .mvnw is there
                    return fileSystem.exists(workingCopy.resolve("mvnw").toString())
                            .chain(exists -> {
                                if (exists) {
                                    buildToolDetector.toolVersions.computeIfAbsent("maven-wrapper", k -> new ConcurrentHashSet<>());
                                }
                                return Uni.createFrom().item(new BuildInfo(rootDirs, buildToolDetector, javaDistroDetector, oses));
                            });
                });
    }

    Uni<Void> analyzeJars(BuildGroup<FqScmRef> buildGroup, BuildInfo buildInfo) {

        List<Uni<Gavtcf>> jars = buildGroup.artifacts().stream()
                .filter(gavtc -> gavtc.getType().getValueOrDefault().equals("jar")
                        && (gavtc.getClassifier() == null || "javadoc".equals(gavtc.getClassifier())))
                .map(referenceMavenRepository::resolve)
                .toList();

        Uni<Void> jarInfos = Multi.createFrom().iterable(jars)
                .onItem().transformToUni(jar -> jar.map(gavtc -> gavtc.getFile())
                        .chain(jarFile -> extractFile(jarFile, "META-INF/MANIFEST.MF", byte[].class))
                        .chain(manifest -> {

                            if (JrebuildUtils.contains(manifest, (byte) '\r')) {
                                buildInfo.oses.add(Os.WINDOWS);
                            }
                            try {
                                Manifest mf = new Manifest(new ByteArrayInputStream(manifest));
                                Attributes attrs = mf.getMainAttributes();
                                buildInfo.buildToolDetector.eval(attrs::getValue);
                                buildInfo.javaDistroDetector.eval(attrs::getValue);

                                detectOs(attrs::getValue, buildInfo.oses::add);
                                // String osArch = attrs.getValue("Os-Arch");
                                return null;// new OsAndJava(os, Optional.ofNullable(javaVersions),
                                            // Optional.ofNullable(distro));
                            } catch (IOException e) {
                                throw new UncheckedIOException("Could not load manifest from " + manifest, e);
                            }
                            return Uni.createFrom().voidItem();
                        }))
                .merge().collect().last();

        List<Uni<Gavtcf>> javadocJars = buildGroup.artifacts().stream()
                .filter(gavtc -> gavtc.getType().getValueOrDefault().equals("jar") && "javadoc".equals(gavtc.getClassifier()))
                .map(referenceMavenRepository::resolve)
                .toList();
        Uni<Void> javadocInfos = Multi.createFrom().iterable(javadocJars)
                .onItem().transformToUni(jar -> jar.map(gavtc -> gavtc.getFile())
                        .chain(jarFile -> extractFile(jarFile, "index.html", String.class))
                        .chain(html -> {
                            if (html.indexOf('\r') >= 0) {
                                buildInfo.oses.add(Os.WINDOWS);
                            }
                            Matcher m = TextResourceMatchService.JAVADOC_GENERATED_BY_TIMESTAMP_PATTERN.matcher(html);
                            if (m.find()) {
                                buildInfo.javaDistroDetector.toolVersions.computeIfAbsent("unknown", k -> new ConcurrentHashSet<>())
                                        .add(m.group(1));
                            }
                            return Uni.createFrom().voidItem();
                        }))
                .merge().collect().last();
        return Uni.combine().all().unis(jarInfos, javadocInfos)
                .with((i1, i2) -> {
                    return i1;
                });

//        .chain(v -> {
//            Set<Os> oses = new TreeSet<>();
//            Set<ComparableVersion> versions = new TreeSet<>(Comparator.reverseOrder());
//            Set<String> distros = new TreeSet<>();
//            for (OsAndJava osAndJava : osAndJavas) {
//                oses.add(osAndJava.os);
//                osAndJava.javaVersion.map(ComparableVersion::new).ifPresent(versions::add);
//                osAndJava.javaDistro.ifPresent(distros::add);
//            }
//            final Optional<String> version = versions.isEmpty() ? Optional.<String> empty()
//                    : Optional.ofNullable(versions.iterator().next().toString());
//            if (distros.isEmpty()) {
//                foojayDiscoService.findDistributionNamesThatSupportVersion(null);
//            }
//            return Uni.createFrom().item(new OsAndJava(
//                    oses.contains(Os.WINDOWS) ? Os.WINDOWS : Os.LINUX,
//                    version,
//                    Optional.of(distros.iterator().next())));
//        })
        // Uni.createFrom().item(() -> {
        // buildGroup. ;
        // });

        // find some published javadoc.jar in the buildGroup and referenceMavenRepository
        // look for "Generated by javadoc \\(([^)])\\) on" pattern
        // do it for all files and collect artifact -> java version mapping

        // Look into MANIFEST.MF
        // Check EOLs \r\n may mean windows
        // Java-Version: 8 is the ClassFile level, we may take it if there is no better hint
        // check the Build-Jdk-Spec: 11 key and take the latest from SDK man if there is no better hint

        // check for muti-release jars -> we should probably take the highest found major if there is no better hint

        // get the commit date and exclude all JDK versions that were published after that date
    }

    static void detectOs(Function<String, String> getAttribute, Consumer<Os> oses) {
        Stream.of("Os-Name", "Build-OS", "Built-OS", "X-Build-OS")
                .map(getAttribute)
                .filter(val -> val != null)
                .map(val -> val.toLowerCase(Locale.ROOT))
                .forEach(val -> {
                    if (val.contains("windows")) {
                        oses.accept(Os.WINDOWS);
                    } else if (val.contains("mac")) {
                        oses.accept(Os.MACOS);
                    }
                });
    }

    static String vendorToDistro(String vendor) {
        // TODO
        /*
         * Sun Microsystems Inc. (pre-2010, old jars)
         * Oracle Corporation (Oracle JDK — and a huge number of OpenJDK builds that never changed the vendor property)
         * Eclipse Adoptium / AdoptOpenJDK (Temurin and its predecessor)
         * Azul Systems, Inc. (Zulu)
         * Amazon.com Inc. (Corretto)
         * BellSoft (Liberica)
         * IBM Corporation (IBM JDK / Semeru)
         * Red Hat, Inc. (Red Hat OpenJDK)
         * Microsoft (Microsoft Build of OpenJDK)
         * GraalVM Community / Oracle GraalVM / Oracle Labs
         * Alibaba (Dragonwell), SAP SE (SapMachine), Tencent (Kona), JetBrains s.r.o. (JetBrains Runtime)
         * occasionally N/A or an empty parenthetical from bare OpenJDK builds
         */
        return vendor;
    }

    <T> Uni<T> extractFile(Path jarFile, String path, Class<T> resultType) {
        return Uni.createFrom()
                .item(() -> {
                    try (ZipFile zipFile = ZipFile.builder().setFile(jarFile.toFile()).get()) {
                        ZipArchiveEntry entry = zipFile.getEntry(path);
                        if (entry == null) {
                            return null;
                        }
                        try (InputStream is = zipFile.getInputStream(entry)) {
                            if (resultType == String.class) {
                                return (T) new String(is.readAllBytes(), StandardCharsets.UTF_8);
                            } else if (resultType == byte[].class) {
                                return (T) is.readAllBytes();
                            } else {
                                throw new IllegalStateException("Expected resultType byte[] or String; found " + resultType);
                            }
                        }
                    } catch (IOException e) {
                        throw new UncheckedIOException("Could extract " + path + " from " + jarFile, e);
                    }
                })
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    Uni<String> detectBuildScript(BuildTool bt, BuildGroup<FqScmRef> buildGroup, Os os, Arch arch,
            Shell shell) {

        StringBuilder cmd = new StringBuilder(bt.command());
        if (os == Os.WINDOWS && ("mvn".equals(cmd.toString()) || "./mvnw".equals(cmd.toString()))) {
            cmd.append(".cmd");
        }
        cmd.append(
                " clean deploy -ntp -DskipTests -Dgpg.skip -DskipPublishing -Dcheckstyle.skip -Dpmd.skip deploy:deploy -DaltDeploymentRepository=local::${DEPLOYMENT_REPO}");

        Set<String> releaseProfiles = new LinkedHashSet<>();
        if (buildGroup.hasJavaDoc()) {
            // TODO: figure out whether there is some special profile for javadoc and sources
        }
        if (buildGroup.hasSources()) {
            // TODO: figure out whether there is some special profile for javadoc and sources
        }
        releaseProfiles.forEach(profile -> cmd.append(" -P").append(profile));

        return Uni.createFrom().item(cmd.toString());
    }

    /**
     * @param rootDir absolute or relative path to start searching from
     * @return list of absolute/relative paths (same style as rootDir) to the
     *         nearest pom.xml files found
     */
    public Uni<Set<Path>> findRootDirectories(Path rootDir, Function<Path, Uni<Boolean>> isRootDir) {
        List<Path> results = new ArrayList<>();
        return search(rootDir, isRootDir, results::add)
                .replaceWith(() -> Collections.unmodifiableSet(results.stream()
                        .map(rootDir::relativize)
                        .collect(Collectors.toCollection(() -> new TreeSet<>(BY_LENGTH_AND_PATH_COMPARATOR)))));
    }

    private static final Comparator<Path> BY_LENGTH_AND_PATH_COMPARATOR = Comparator.comparing(Path::getNameCount)
            .thenComparing(path -> path);

    private Uni<Void> search(Path dir, Function<Path, Uni<Boolean>> isRootDir, Consumer<Path> results) {

        return isRootDir.apply(dir)
                .chain(isRootDirSatisfied -> {
                    if (isRootDirSatisfied) {
                        // stop descending further into this directory.
                        results.accept(dir);
                        return Uni.createFrom().voidItem();
                    }
                    // This dir does not satisfy isRootDir — look at subdirectories.
                    return fileSystem.readDir(dir.toString())
                            .flatMap(files -> {
                                List<Uni<Void>> childSearches = files.stream()
                                        .map(Path::of)
                                        .map(path -> isSearchableDir(path)
                                                .flatMap(searchable -> searchable
                                                        ? search(path, isRootDir, results)
                                                        : Uni.createFrom().<Void> voidItem()))
                                        .toList();

                                if (childSearches.isEmpty()) {
                                    return Uni.createFrom().voidItem();
                                }
                                return Uni.combine().all()
                                        .unis(childSearches)
                                        .discardItems();
                            });
                });
    }

    private Uni<Boolean> isSearchableDir(Path path) {
        String name = path.getFileName().toString();
        if (name.startsWith(".")) {
            return Uni.createFrom().item(false);
        }
        return fileSystem.props(path.toString())
                .map(props -> props.isDirectory())
                // Broken symlinks / permission errors -> just skip that entry.
                .onFailure().recoverWithItem(false);
    }

    public record BuildInfo(
            Set<Path> rootPaths,
            ToolVersionDetector<BuildTool> buildToolDetector,
            ToolVersionDetector<String> javaDistroDetector,
            Set<Os> oses) {

        public Optional<BuildToolAndVersion> buildTool() {
            Map<BuildTool, Set<String>> buildTools = buildToolDetector.toolVersions;
            switch (buildTools.size()) {
            case 0: {
                return Optional.empty();
            }
            case 1: {
                Entry<BuildTool, Set<String>> en = buildTools.entrySet().iterator().next();
                return Optional.of(new BuildToolAndVersion(en.getKey(), newestVersion(en.getValue()).toString()));
            }
            default:

                // prefer wrapper
                for (BuildTool bt : buildTools.keySet()) {
                    if (bt.isWrapper()) {
                        return Optional.of(new BuildToolAndVersion(bt, null));
                    }
                }

                // otherwise the first one with a version
                for (Entry<BuildTool, Set<String>> en : buildTools.entrySet()) {
                    if (!en.getValue().isEmpty()) {
                        return Optional.of(new BuildToolAndVersion(en.getKey(), newestVersion(en.getValue()).toString()));
                    }
                }
                return Optional.empty();
            }
        }

        public Optional<JavaDistroAndVersion> javaDistroAndVersion() {
            Map<String, Set<String>> distrosAndVersions = javaDistroDetector.toolVersions;
            switch (distrosAndVersions.size()) {
            case 0: {
                return Optional.empty();
            }
            case 1: {
                Entry<String, Set<String>> en = distrosAndVersions.entrySet().iterator().next();
                return Optional.of(new JavaDistroAndVersion(vendorToDistro(en.getKey()), newestVersion(en.getValue()).toString()));
            }
            default:
                // do we have a version on Unknown?
                Set<String> versionsOfUnknownDistro = distrosAndVersions.remove("unknown");
                if (versionsOfUnknownDistro != null) {
                    // add the versionsOfUnknownDistro to other distros unless they already have versions
                    distrosAndVersions.entrySet().stream()
                    .map(Entry::getValue)
                    .filter(Collection::isEmpty)
                    .forEach(vals -> vals.addAll(versionsOfUnknownDistro));
                }
                if (distrosAndVersions.size() == 1) {
                    Entry<String, Set<String>> en = distrosAndVersions.entrySet().iterator().next();
                    return Optional.of(new JavaDistroAndVersion(vendorToDistro(en.getKey()), newestVersion(en.getValue()).toString()));
                }
                /* Get the distro with the newest version */
                return distrosAndVersions.entrySet().stream()
                    .map(en -> new AbstractMap.SimpleImmutableEntry<>(en.getKey(), newestVersion(en.getValue())))
                    .max((a,b) -> a.getValue().compareTo(b.getValue()))
                    .map(en -> new JavaDistroAndVersion(vendorToDistro(en.getKey()), en.getValue().toString()));
            }
        }

        public Arch arch() {
            return Arch.amd64;
        }

        public Os os() {
            switch (oses.size()) {
            case 0: {
                return Os.LINUX;
            }
            case 1: {
                return oses.iterator().next();
            }
            default:
                return oses.contains(Os.WINDOWS) ? Os.WINDOWS : oses.contains(Os.MACOS) ? Os.MACOS : Os.LINUX;
            }
        }

        private ComparableVersion newestVersion(Set<String> versions) {
            if (versions == null || versions.isEmpty()) {
                return null;
            }
            if (versions.size() == 1) {
                return new ComparableVersion(versions.iterator().next());
            }
            return versions.stream().map(ComparableVersion::new).max((a, b) -> a.compareTo(b)).get();
        }

        Uni<Void> completeToolVersions(Git git, BuildInfo buildInfo, Supplier<Uni<ZonedDateTime>> lastCommitDate, MavenVersionsService mavenVersionsService) {
            Optional<BuildToolAndVersion> bt = buildTool();
            boolean btNeedsVersion = bt.isPresent() && bt.get().version() == null;
            Optional<JavaDistroAndVersion> javaDistro = javaDistroAndVersion();
            boolean javaNeedsVersion = javaDistro.isPresent() && javaDistro.get().version() == null;
            /* Guess by date */

            if (btNeedsVersion || javaNeedsVersion) {
                return GitUtils.lastCommitDate(git)
                  .chain(lastCommitDate -> {

                      Uni<Void> btUni = btNeedsVersion
                              ?             mavenVersionsService.findLatestAtDate(lastCommitDate)
                                      .map(version -> buildInfo.buildToolDetector.toolVersions.computeIfAbsent(bt.get().buildTool(), k -> new ConcurrentHashSet<>()).add(version))
                                      .replaceWithVoid()

                                      : Uni.createFrom().voidItem();
                      Uni<Void> javaUni = btNeedsVersion
                              ?             mavenVersionsService.findLatestAtDate(lastCommitDate)
                                      .map(version -> buildInfo.buildToolDetector.toolVersions.computeIfAbsent(bt.get().buildTool(), k -> new ConcurrentHashSet<>()).add(version))
                                      .replaceWithVoid()

                                      : Uni.createFrom().voidItem();

                      Uni.combine().all().unis(btUni, javaUni)
                      .with((i1, i2) -> {
                          return i1;
                      });
                  });
            }
            return Uni.createFrom().voidItem();
        }

    }

    record BuildToolAndVersion(BuildTool buildTool, String version) {
        Optional<Tool> tool() {
            return buildTool.tool(version);
        }
    }
    record JavaDistroAndVersion(String distro, String version) {
        Optional<Tool> tool() {
            return Optional.of(new Tool("sdkman", "java", version + "-"+ distro));
        }
    }

    static class ToolVersionDetector<T> {
        static final BiFunction<Matcher, Function<String, String>, String> VENDOR_FOR_JAVA_VERSION = (m, getAttribute) -> {
            final String vendor = getAttribute.apply("Java-Vendor");
            return vendor == null ? "unknown" : vendor;
        };
        static final List<ToolSearchPattern<String>> JAVA_DISTRO_VERSION_DETECTOR = List.of(
                new ToolSearchPattern<String>("Created-By", 1, s -> s, Pattern.compile("([^ ]+) \\(([^\\)])\\)"), 2),
                new ToolSearchPattern<String>(
                        List.of(
                                "Build-Java-Version",
                                "Java-Version",
                                "JDK-Version",
                                "Build-Jdk",
                                "Build-Jdk-Spec",
                                "X-Build-Jdk",
                                "X-Compile-Source",
                                "X-Compile-Source-JDK",
                                "X-Compile-Target",
                                "X-Compile-Target-JDK"),
                        VENDOR_FOR_JAVA_VERSION,
                        Pattern.compile(".*"),
                        0),
                new ToolSearchPattern<String>("Build-Info", VENDOR_FOR_JAVA_VERSION, Pattern.compile(".* javac ([^ ]+)"), 1)
        // needs some more parsing
        // new ToolSearchPattern("Bundle-RequiredExecutionEnvironment", 1, Pattern.compile("([^ ]+) \\(([^\\)])\\)"),
        // 2),
        // String javaHome = attrs.getValue("JAVA_*_HOME");
        );
        static final List<ToolSearchPattern<BuildTool>> BUILD_TOOL_VERSION_DETECTOR = List.of(
                new ToolSearchPattern<BuildTool>("Build-Tool", BuildTool.maven, Pattern.compile("Apache Maven +([^ ]+)"), 1),
                new ToolSearchPattern<BuildTool>("Created-By", BuildTool.maven, Pattern.compile("Apache Maven +([^ ]+)"), 1),
                new ToolSearchPattern<BuildTool>("X-Builder", BuildTool.maven, Pattern.compile("Maven +([^ ]+)"), 1),
                new ToolSearchPattern<BuildTool>("Maven-Version", BuildTool.maven, Pattern.compile(".*"), 0),

                new ToolSearchPattern<BuildTool>("Ant-Version", BuildTool.ant, Pattern.compile(".*"), 0),
                new ToolSearchPattern<BuildTool>("Created-By", BuildTool.ant, Pattern.compile("Ant +([^ ]+)"), 1),
                new ToolSearchPattern<BuildTool>("Created-By", BuildTool.ant, Pattern.compile("Apache Ant +([^ ]+)"), 1),
                new ToolSearchPattern<BuildTool>("Created-By", BuildTool.ant, Pattern.compile("Ant"), -1),

                new ToolSearchPattern<BuildTool>("Gradle-Version", BuildTool.gradle, Pattern.compile(".*"), 0),
                new ToolSearchPattern<BuildTool>("Built-Gradle", BuildTool.gradle, Pattern.compile(".*"), 0),
                new ToolSearchPattern<BuildTool>("Created-By", BuildTool.gradle, Pattern.compile("Gradle +([^ ]+)"), 1),
                new ToolSearchPattern<BuildTool>("Created-By", BuildTool.gradle, Pattern.compile("Gradle"), -1));

        /** Tool name (for Java, the distribution name) and its versions found */
        private final Map<T, Set<String>> toolVersions = new TreeMap<>();
        private final Map<String, List<ToolSearchPattern<T>>> patternsByAttribute = new TreeMap<>();

        public static ToolVersionDetector<BuildTool> createBuildToolDetector() {
            return new ToolVersionDetector<>(BUILD_TOOL_VERSION_DETECTOR);
        }

        public static ToolVersionDetector<String> createJavaDistroDetector() {
            return new ToolVersionDetector<>(JAVA_DISTRO_VERSION_DETECTOR);
        }

        ToolVersionDetector(List<ToolSearchPattern<T>> patterns) {
            for (ToolSearchPattern<T> pattern : patterns) {
                for (String attr : pattern.attributeNames) {
                    patternsByAttribute.computeIfAbsent(attr, k -> new ArrayList<>()).add(pattern);
                }
            }
        }

        void eval(Function<String, String> getAttribute) {

            for (Entry<String, List<ToolSearchPattern<T>>> searchPatternEntry : patternsByAttribute.entrySet()) {
                String val = getAttribute.apply(searchPatternEntry.getKey());
                if (val != null) {
                    for (ToolSearchPattern<T> pattern : searchPatternEntry.getValue()) {
                        pattern.eval(val, getAttribute, (k, v) -> {
                            Set<String> vals = toolVersions.computeIfAbsent(k, kk -> new TreeSet<String>());
                            if (v != null) {
                                vals.add(v);
                            }
                        });
                    }
                }
            }

        }
    }

    public record ToolSearchPattern<T>(List<String> attributeNames, BiFunction<Matcher, Function<String, String>, T> toolName,
            Pattern pattern, int group) {
        public ToolSearchPattern(String attributeName, BiFunction<Matcher, Function<String, String>, T> toolName,
                Pattern pattern, int group) {
            this(List.of(attributeName), toolName, pattern, group);
        }

        public ToolSearchPattern(String attributeName, T toolName, Pattern pattern, int group) {
            this(List.of(attributeName), (m, getAttribute) -> toolName, pattern, group);
        }

        public ToolSearchPattern(String attributeName, int toolNameGroup, Function<String, T> deserialize, Pattern pattern, int group) {
            this(List.of(attributeName), (m, getAttribute) -> deserialize.apply(m.group(toolNameGroup)), pattern, group);
        }

        public boolean eval(String input, Function<String, String> getAttribute, BiConsumer<T, String> consumer) {
            Matcher m = pattern.matcher(input);
            if (m.matches()) {
                T name = toolName.apply(m, getAttribute);
                if (group < 0) {
                    consumer.accept(name, null);
                } else {
                    consumer.accept(name, m.group(group));
                }
                return true;
            }
            return false;
        }
    }

    public record OsAndJava(Os os, Optional<String> javaVersion, Optional<String> javaDistro) {

    }
}
