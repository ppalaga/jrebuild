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
import java.time.ZonedDateTime;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.eclipse.jgit.api.Git;
import org.l2x6.jrebuild.api.os.Arch;
import org.l2x6.jrebuild.api.os.Os;
import org.l2x6.jrebuild.api.os.Shell;
import org.l2x6.jrebuild.api.os.Tool;
import org.l2x6.jrebuild.api.scm.FqScmRef;
import org.l2x6.jrebuild.api.util.ComparableVersion;
import org.l2x6.jrebuild.common.StackTraceLessException;
import org.l2x6.jrebuild.common.git.GitUtils;
import org.l2x6.jrebuild.core.build.*;
import org.l2x6.jrebuild.core.build.service.ResourceMatchService.BaseResourceMatchService.TextResourceMatchService;
import org.l2x6.jrebuild.core.maven.ReferenceMavenRepository;
import org.l2x6.jrebuild.core.scm.CloneDirectoriesLayout;
import org.l2x6.pom.tuner.model.Gavtcf;

public record GuessBuildRequestService(
        FileSystem fileSystem,
        CloneDirectoriesLayout cloneDirectoriesLayout,
        ReferenceMavenRepository referenceMavenRepository,
        BuildToolVersionsService buildToolVersionsService,
        FindReferenceArtifactsService findReferenceArtifactsService,
        FoojayDiscoService foojayDiscoService) {

    public Uni<BuildRequestAlternatives> guess(FqScmRef scmRef) {

        return cloneDirectoriesLayout.lockDirectory(scmRef.repository().uri())
                .chain(cloneDir -> GitUtils
                        .cloneOrFetchAndResetAsync(scmRef, cloneDir.cloneDirectory(), -1)
                        .chain(git -> analyzeSourceTree(scmRef, git)
                                .chain(buildRequestBuilder -> findReferenceArtifactsService
                                        .findPublishedArtifacts(scmRef,
                                                SourceRootDirectories.roots(buildRequestBuilder.rootPaths), cloneDir)
                                        .chain(buildGroup -> {
                                            buildRequestBuilder.buildGroup(buildGroup);
                                            return analyzeJars(buildGroup, buildRequestBuilder)
                                                    .chain(_ -> buildRequestBuilder
                                                            .build(GitUtils.lastCommitDate(git).memoize().indefinitely()));
                                        }))
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

    Uni<BuildRequestAlternativesBuilder> analyzeSourceTree(FqScmRef scmRef, Git git) {
        Path workingCopy = git.getRepository().getWorkTree().toPath();
        return detectMaven(workingCopy)
                .onItem().ifNull().switchTo(() -> detectGradle(workingCopy))
                .onItem().ifNull().switchTo(() -> detectAnt(workingCopy))
                .onItem().ifNull()
                .switchTo(Uni.createFrom().failure(new StackTraceLessException("Could not detect build tool for " + scmRef)));
    }

    Uni<BuildRequestAlternativesBuilder> detectGradle(Path workingCopy) {
        return Uni.createFrom().nullItem();
    }

    Uni<BuildRequestAlternativesBuilder> detectAnt(Path workingCopy) {
        return Uni.createFrom().nullItem();
    }

    Uni<BuildRequestAlternativesBuilder> detectMaven(Path workingCopy) {
        return findRootDirectories(workingCopy, dir -> fileSystem.exists(dir.resolve("pom.xml").toString()))
                .chain(rootDirs -> {
                    if (rootDirs.isEmpty()) {
                        return Uni.createFrom().nullItem();
                    }
                    // Check whether .mvnw is there
                    return fileSystem.exists(workingCopy.resolve("mvnw").toString())
                            .chain(exists -> {
                                BuildRequestAlternativesBuilder result = new BuildRequestAlternativesBuilder(
                                        foojayDiscoService,
                                        buildToolVersionsService,
                                        rootDirs);
                                if (exists) {
                                    result.buildToolDetector.toolVersions.computeIfAbsent(BuildTool.maven_wrapper,
                                            _ -> new ConcurrentHashSet<>());
                                }
                                return Uni.createFrom().item(result);
                            });
                });
    }

    Uni<Void> analyzeJars(BuildGroup<FqScmRef> buildGroup, BuildRequestAlternativesBuilder buildInfo) {

        List<Uni<Gavtcf>> jars = buildGroup.artifacts().stream()
                .filter(gavtc -> gavtc.getType().getValueOrDefault().equals("jar")
                        && (gavtc.getClassifier() == null || "javadoc".equals(gavtc.getClassifier())))
                .map(referenceMavenRepository::resolve)
                .toList();

        Uni<Void> jarInfos = Multi.createFrom().iterable(jars)
                .onItem().transformToUni(jar -> jar.map(gavtc -> gavtc.getFile())
                        .chain(jarFile -> extractFile(jarFile, "META-INF/MANIFEST.MF", byte[].class)
                                .chain(manifest -> {

                                    try {
                                        Manifest mf = new Manifest(new ByteArrayInputStream(manifest));
                                        Attributes attrs = mf.getMainAttributes();
                                        buildInfo.buildToolDetector.detect(attrs::getValue);
                                        buildInfo.javaDistroDetector.detect(attrs::getValue);

                                        buildInfo.detectOs(attrs::getValue);
                                    } catch (IOException e) {
                                        throw new UncheckedIOException(
                                                "Could not load manifest from " + jarFile + "!META-INF/MANIFEST.MF", e);
                                    }
                                    return Uni.createFrom().voidItem();
                                })))
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
                                buildInfo.javaDistroDetector.toolVersions
                                        .computeIfAbsent(JavaDistroAndVersion.UNKNOWN, k -> new ConcurrentHashSet<>())
                                        .add(new ComparableVersion(m.group(1)));
                            }
                            return Uni.createFrom().voidItem();
                        }))
                .merge().collect().last();
        return Uni.combine().all().unis(jarInfos, javadocInfos)
                .with((i1, _) -> i1);

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

    /**
     * @param  rootDir absolute or relative path to start searching from
     * @return         list of absolute/relative paths (same style as rootDir) to the
     *                 nearest pom.xml files found
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

    public static final class BuildRequestAlternativesBuilder {

        private BuildGroup<FqScmRef> buildGroup;
        private final FoojayDiscoService foojayDiscoService;
        private final BuildToolVersionsService mavenVersionsService;
        private final Set<Path> rootPaths;
        private final ToolVersionDetector<BuildTool> buildToolDetector;
        private final ToolVersionDetector<String> javaDistroDetector;
        private final Set<Os> oses = new ConcurrentHashSet<>();

        public BuildRequestAlternativesBuilder(
                FoojayDiscoService foojayDiscoService,
                BuildToolVersionsService buildToolsVersionsService,
                Set<Path> rootPaths) {
            this.foojayDiscoService = foojayDiscoService;
            this.mavenVersionsService = buildToolsVersionsService;
            this.rootPaths = rootPaths;
            this.buildToolDetector = ToolVersionDetector.createBuildToolDetector();
            this.javaDistroDetector = ToolVersionDetector.createJavaDistroDetector();
        }

        public Uni<Set<BuildToolAndVersion>> buildTool(Uni<ZonedDateTime> releaseDate) {
            Map<BuildTool, Set<ComparableVersion>> buildTools = buildToolDetector.toolVersions;
            if (buildTools.isEmpty()) {
                return Uni.createFrom().item(Set.of());
            }
            List<Uni<BuildToolAndVersion>> result = new ArrayList<>();
            // prefer wrapper
            for (Entry<BuildTool, Set<ComparableVersion>> en : buildTools.entrySet()) {
                BuildTool bt = en.getKey();
                Set<ComparableVersion> versions = en.getValue();
                if (versions.isEmpty()) {
                    if (bt.isWrapper()) {
                        result.add(Uni.createFrom().item(new BuildToolAndVersion(bt, null)));
                    } else {
                        Uni<BuildToolAndVersion> item = releaseDate
                                .chain(date -> mavenVersionsService.latestVersionAsOf(bt, date))
                                .map(version -> new BuildToolAndVersion(bt, new ComparableVersion(version)));
                        result.add(item);
                    }
                } else {
                    for (ComparableVersion v : versions) {
                        result.add(Uni.createFrom().item(new BuildToolAndVersion(bt, v)));
                    }
                }
            }
            return Uni.join().all(result)
                    .andCollectFailures()
                    .map(list -> Collections.unmodifiableSet(new TreeSet<>(list)));
        }

        public Uni<Set<JavaDistroAndVersion>> javaDistroAndVersion(Uni<ZonedDateTime> releaseDate) {
            Map<String, Set<ComparableVersion>> distrosAndVersions = javaDistroDetector.toolVersions;
            // do we have a version on Unknown?
            {
                Set<ComparableVersion> versionsOfUnknownDistro = distrosAndVersions.remove(JavaDistroAndVersion.UNKNOWN);
                if (versionsOfUnknownDistro != null) {
                    // add the versionsOfUnknownDistro to other distros unless they already have versions
                    distrosAndVersions.values().stream()
                            .filter(Collection::isEmpty)
                            .forEach(vals -> vals.addAll(versionsOfUnknownDistro));
                    if (distrosAndVersions.isEmpty()) {
                        distrosAndVersions.put(FoojayDiscoService.KnownJvmVendor.TEMURIN.toString(), versionsOfUnknownDistro);
                    }
                }
            }
            Set<ComparableVersion> versionsOfUnknownDistro = new TreeSet<>(Comparator.reverseOrder());
            Map<String, Set<ComparableVersion>> intermediaryResult = new LinkedHashMap<>();
            for (Entry<String, Set<ComparableVersion>> en : distrosAndVersions.entrySet()) {
                String distro = foojayDiscoService.vendorToDistro(en.getKey());
                if (distro == null) {
                    versionsOfUnknownDistro.addAll(en.getValue());
                } else {
                    intermediaryResult.computeIfAbsent(distro, _ -> new TreeSet<>()).addAll(en.getValue());
                }
            }
            if (!versionsOfUnknownDistro.isEmpty()) {
                // add the versionsOfUnknownDistro to other distros unless they already have versions
                intermediaryResult.values().stream()
                        .filter(Collection::isEmpty)
                        .forEach(vals -> vals.addAll(versionsOfUnknownDistro));
                if (intermediaryResult.isEmpty()) {
                    intermediaryResult.put(FoojayDiscoService.KnownJvmVendor.TEMURIN.toString(), versionsOfUnknownDistro);
                }
            }

            List<Uni<JavaDistroAndVersion>> result = new ArrayList<>();
            for (Entry<String, Set<ComparableVersion>> en : intermediaryResult.entrySet()) {
                String distro = en.getKey();
                Set<ComparableVersion> versions = en.getValue();
                if (versions.isEmpty()) {
                    Uni<JavaDistroAndVersion> item = releaseDate
                            .chain(foojayDiscoService::latestJavaDistroAndVersionAsOf)
                            .map(jdv -> new JavaDistroAndVersion(distro, jdv.version()));
                    result.add(item);
                } else {
                    for (ComparableVersion v : versions) {
                        result.add(Uni.createFrom().item(new JavaDistroAndVersion(distro, v)));
                    }
                }
            }
            if (result.isEmpty()) {
                Uni<JavaDistroAndVersion> item = releaseDate
                        .chain(foojayDiscoService::latestJavaDistroAndVersionAsOf);
                result.add(item);
            }

            return Uni.join().all(result).andCollectFailures().map(list -> Collections.unmodifiableSet(new TreeSet<>(list)));
        }

        public void detectOs(Function<String, String> getManifestAttribute) {
            Stream.of("Os-Name", "Build-OS", "Built-OS", "X-Build-OS")
                    .map(getManifestAttribute)
                    .filter(val -> val != null)
                    .map(val -> val.toLowerCase(Locale.ROOT))
                    .forEach(val -> {
                        if (val.contains("windows")) {
                            oses.add(Os.WINDOWS);
                        } else if (val.contains("mac")) {
                            oses.add(Os.MACOS);
                        }
                    });
        }

        public Arch arch() {
            return Arch.amd64;
        }

        public Os os() {
            return switch (oses.size()) {
            case 0 -> Os.LINUX;
            case 1 -> oses.iterator().next();
            default -> oses.contains(Os.WINDOWS) ? Os.WINDOWS : oses.contains(Os.MACOS) ? Os.MACOS : Os.LINUX;
            };
        }

        public void buildGroup(BuildGroup<FqScmRef> buildGroup) {
            this.buildGroup = buildGroup;
        }

        Uni<BuildRequestAlternatives> build(Uni<ZonedDateTime> releaseDate) {
            // releaseDate GitUtils.lastCommitDate(git);
            Uni<Set<BuildToolAndVersion>> buildTools = buildTool(releaseDate);
            Uni<Set<JavaDistroAndVersion>> javaDistros = javaDistroAndVersion(releaseDate);

            return Uni.combine()
                    .all().unis(buildTools, javaDistros).asTuple()
                    .onItem().transform(tuple -> {
                        Set<BuildToolAndVersion> bts = tuple.getItem1();
                        Os os = os();
                        Shell shell = os.defaultShell();

                        return new BuildRequestAlternatives(
                                buildGroup,
                                os,
                                arch(),
                                shell,
                                tuple.getItem1(),
                                tuple.getItem2());
                    });

        }

    }

    static class ToolVersionDetector<T> {
        static final BiFunction<Matcher, Function<String, String>, String> VENDOR_FOR_JAVA_VERSION = (m, getAttribute) -> {
            final String vendor = getAttribute.apply("Java-Vendor");
            return vendor == null ? JavaDistroAndVersion.UNKNOWN : vendor;
        };
        static final List<ToolSearchPattern<String>> JAVA_DISTRO_VERSION_DETECTOR = List.of(
                new ToolSearchPattern<>("Created-By", 1, s -> s, Pattern.compile("([^ ]+) \\(([^\\)])\\)"), 2),
                new ToolSearchPattern<>(
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
                new ToolSearchPattern<>("Build-Info", VENDOR_FOR_JAVA_VERSION, Pattern.compile(".* javac ([^ ]+)"), 1)
        // needs some more parsing
        // new ToolSearchPattern("Bundle-RequiredExecutionEnvironment", 1, Pattern.compile("([^ ]+) \\(([^\\)])\\)"),
        // 2),
        // String javaHome = attrs.getValue("JAVA_*_HOME");
        );
        static final List<ToolSearchPattern<BuildTool>> BUILD_TOOL_VERSION_DETECTOR = List.of(
                new ToolSearchPattern<>("Build-Tool", BuildTool.maven, Pattern.compile("Apache Maven +([^ ]+)"), 1),
                new ToolSearchPattern<>("Created-By", BuildTool.maven, Pattern.compile("Apache Maven +([^ ]+)"), 1),
                new ToolSearchPattern<>("X-Builder", BuildTool.maven, Pattern.compile("Maven +([^ ]+)"), 1),
                new ToolSearchPattern<>("Maven-Version", BuildTool.maven, Pattern.compile(".*"), 0),

                new ToolSearchPattern<>("Ant-Version", BuildTool.ant, Pattern.compile(".*"), 0),
                new ToolSearchPattern<>("Created-By", BuildTool.ant, Pattern.compile("Ant +([^ ]+)"), 1),
                new ToolSearchPattern<>("Created-By", BuildTool.ant, Pattern.compile("Apache Ant +([^ ]+)"), 1),
                new ToolSearchPattern<>("Created-By", BuildTool.ant, Pattern.compile("Ant"), -1),

                new ToolSearchPattern<>("Gradle-Version", BuildTool.gradle, Pattern.compile(".*"), 0),
                new ToolSearchPattern<>("Built-Gradle", BuildTool.gradle, Pattern.compile(".*"), 0),
                new ToolSearchPattern<>("Created-By", BuildTool.gradle, Pattern.compile("Gradle +([^ ]+)"), 1),
                new ToolSearchPattern<>("Created-By", BuildTool.gradle, Pattern.compile("Gradle"), -1));

        /**
         * Tool name (for Java, the distribution name) and its versions found
         */
        private final Map<T, Set<ComparableVersion>> toolVersions = new TreeMap<>();
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

        void detect(Function<String, String> getAttribute) {

            for (Entry<String, List<ToolSearchPattern<T>>> searchPatternEntry : patternsByAttribute.entrySet()) {
                String val = getAttribute.apply(searchPatternEntry.getKey());
                if (val != null) {
                    for (ToolSearchPattern<T> pattern : searchPatternEntry.getValue()) {
                        pattern.eval(val, getAttribute, (k, v) -> {
                            Set<ComparableVersion> vals = toolVersions.computeIfAbsent(k,
                                    kk -> new TreeSet<>(Comparator.reverseOrder()));
                            if (v != null) {
                                vals.add(new ComparableVersion(v));
                            }
                        });
                    }
                }
            }

        }
    }

    public record ToolSearchPattern<T>(List<String> attributeNames,
            BiFunction<Matcher, Function<String, String>, T> toolName,
            Pattern pattern, int group) {
        public ToolSearchPattern(String attributeName, BiFunction<Matcher, Function<String, String>, T> toolName,
                Pattern pattern, int group) {
            this(List.of(attributeName), toolName, pattern, group);
        }

        public ToolSearchPattern(String attributeName, T toolName, Pattern pattern, int group) {
            this(List.of(attributeName), (m, getAttribute) -> toolName, pattern, group);
        }

        public ToolSearchPattern(String attributeName, int toolNameGroup, Function<String, T> deserialize, Pattern pattern,
                int group) {
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
