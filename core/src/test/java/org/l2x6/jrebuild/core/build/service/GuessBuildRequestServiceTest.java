/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.build.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.l2x6.jrebuild.api.os.Arch;
import org.l2x6.jrebuild.api.os.Os;
import org.l2x6.jrebuild.api.os.Shell;
import org.l2x6.jrebuild.api.scm.FqScmRef;
import org.l2x6.jrebuild.api.scm.ScmRef;
import org.l2x6.jrebuild.api.scm.ScmRef.Kind;
import org.l2x6.jrebuild.api.scm.ScmRepository;
import org.l2x6.jrebuild.api.util.ComparableVersion;
import org.l2x6.jrebuild.core.build.*;
import org.l2x6.jrebuild.core.build.service.TestEnvironment.RemoteRepository;
import org.l2x6.pom.tuner.MavenRepository;
import org.l2x6.pom.tuner.model.Gavtc;
import org.l2x6.pom.tuner.model.Gavtcf;
import org.l2x6.pom.tuner.model.OptionalWithDefault;

public class GuessBuildRequestServiceTest {

    @Test
    void guess() throws Exception {
        FqScmRef fqScmRef = FqScmRef.of(
                new ScmRef(Kind.TAG, "4.10.0", null),
                ScmRepository.git("https://github.com/l2x6/pom-tuner.git"));
        try (TestEnvironment testEnv = new TestEnvironment(GuessBuildRequestServiceTest.class, RemoteRepository.CENTRAL)) {
            GuessBuildRequestService service = testEnv.getGuessBuildRequestService();
            BuildRequestAlternatives actual = service.guess(fqScmRef).await().indefinitely();

            BuildGroup<FqScmRef> bg = new BuildGroup<FqScmRef>(fqScmRef,
                    Collections.unmodifiableSet(
                            Stream.of("org.l2x6.pom-tuner:pom-tuner:4.10.0:jar",
                                    "org.l2x6.pom-tuner:pom-tuner:4.10.0:jar:javadoc",
                                    "org.l2x6.pom-tuner:pom-tuner:4.10.0:jar:sources",
                                    "org.l2x6.pom-tuner:pom-tuner:4.10.0:pom",
                                    "org.l2x6.pom-tuner:pom-tuner-parent:4.10.0:pom",
                                    "org.l2x6.pom-tuner:pom-tuner-tests:4.10.0:jar",
                                    "org.l2x6.pom-tuner:pom-tuner-tests:4.10.0:pom").map(Gavtc::of)
                                    .collect(Collectors.toCollection(() -> new TreeSet<>(
                                            Gavtc.groupFirstComparator(OptionalWithDefault.valueOrDefaultComparator()))))));
            BuildToolAndVersion bt = new BuildToolAndVersion(BuildTool.maven_wrapper, null);
            BuildRequestAlternatives expected = new BuildRequestAlternatives(
                    bg,
                    Os.LINUX,
                    Arch.amd64,
                    Shell.BASH,
                    Set.of(bt),
                    Stream.of("11.0.25", "11", "8").map(ComparableVersion::new).map(JavaDistroAndVersion::temurin)
                            .collect(Collectors.toCollection(TreeSet::new)));
            Assertions.assertThat(actual).isEqualTo(expected);

            String buildScript = actual.createBuildScript(bt.buildTool());
            Assertions.assertThat(buildScript)
                    .isEqualTo(
                            "./mvnw clean deploy -ntp -DskipTests -Dgpg.skip -DskipPublishing -Dcheckstyle.skip -Dpmd.skip deploy:deploy -DaltDeploymentRepository=local::${DEPLOYMENT_REPO}");
        }

    }

    @Test
    void listManifestEntries() {

        Map<String, CountedValue> values = new ConcurrentHashMap<>();

        MavenRepository repo = MavenRepository.local(Path.of(System.getProperty("user.home")).resolve(".m2/repository"));
        repo.gavtcfStream()
                .filter(gavtcf -> gavtcf.getType().getValueOrDefault().equals("jar") && gavtcf.getClassifier() == null)
                .map(Gavtcf::getFile)
                .peek(f -> System.out.println(f))
                .toList().parallelStream()
                .forEach(file -> {
                    System.err.println("=== file " + file);
                    try (ZipFile zipFile = ZipFile.builder().setFile(file.toFile()).get()) {
                        ZipArchiveEntry zipEntry = zipFile.getEntry("META-INF/MANIFEST.MF");
                        if (zipEntry != null) {
                            try (InputStream is = zipFile.getInputStream(zipEntry)) {
                                Manifest mf = new Manifest(is);
                                mf.getMainAttributes()
                                        .forEach((k, v) -> {
                                            CountedValue entry = values
                                                    .computeIfAbsent(k.toString(),
                                                            kk -> new CountedValue(new AtomicInteger(),
                                                                    new ConcurrentHashMap<>()));
                                            entry.count.incrementAndGet();
                                            entry.values.computeIfAbsent(v.toString(), vv -> new AtomicInteger())
                                                    .incrementAndGet();
                                        });
                            }

                        }
                    } catch (IOException e) {
                        new UncheckedIOException("Could not read " + file, e).printStackTrace();
                    }
                });
        values.entrySet().stream()
                .sorted(Comparator.<Entry<String, CountedValue>, Integer> comparing(en -> en.getValue().count().get())
                        .reversed())
                .forEach(en -> System.out.println(en.getKey() + en.getValue().toString()));
    }

    record CountedValue(AtomicInteger count, Map<String, AtomicInteger> values) {

        @Override
        public String toString() {
            return count.get() + ": \n  - " + values.entrySet().stream()
                    .sorted(Comparator.<Entry<String, AtomicInteger>, Integer> comparing(en -> en.getValue().get()).reversed())
                    .map(en -> en.getValue() + ": " + en.getKey())
                    .collect(Collectors.joining("\n  - "));
        }

    }
}
