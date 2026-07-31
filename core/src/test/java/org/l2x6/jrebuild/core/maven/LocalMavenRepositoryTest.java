/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild
 *                                 project contributors as indicated by the @author tags
 *                                 SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.maven;

import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.core.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.l2x6.pom.tuner.model.Gav;
import org.l2x6.pom.tuner.model.Gavtc.Type;
import org.l2x6.pom.tuner.model.Gavtcf;

public class LocalMavenRepositoryTest {
    @Test
    void local() {
        Path root = Paths.get("target/test-classes/LocalMavenRepositoryTest").toAbsolutePath().normalize();
        io.vertx.core.Vertx coreVertx = io.vertx.core.Vertx.vertx();
        final Vertx vertx = new Vertx(coreVertx);
        FileSystem fs = vertx.fileSystem();
        LocalMavenRepository repo = new LocalMavenRepository(root, fs);
        List<Gavtcf> found = repo.gavtcfStream().collect().asList().await().indefinitely();
        Gav pt490 = Gav.of("org.l2x6.pom-tuner:pom-tuner:4.9.0");
        Gav pt4100 = Gav.of("org.l2x6.pom-tuner:pom-tuner:4.10.0");
        Assertions.assertThat(found).containsExactlyInAnyOrder(
                Gav.of("org.l2x6.pom-tuner:pom-tuner-parent:4.9.0").toGavtc(Type.pom(), null)
                        .toGavtcf(root.resolve("org/l2x6/pom-tuner/pom-tuner-parent/4.9.0/pom-tuner-parent-4.9.0.pom")),
                pt490.toGavtc(Type.pom(), null)
                        .toGavtcf(root.resolve("org/l2x6/pom-tuner/pom-tuner/4.9.0/pom-tuner-4.9.0.pom")),
                pt490.toGavtc(Type.jar(), null)
                        .toGavtcf(root.resolve("org/l2x6/pom-tuner/pom-tuner/4.9.0/pom-tuner-4.9.0.jar")),
                pt490.toGavtc(Type.jar(), "sources")
                        .toGavtcf(root.resolve("org/l2x6/pom-tuner/pom-tuner/4.9.0/pom-tuner-4.9.0-sources.jar")),

                Gav.of("org.l2x6.pom-tuner:pom-tuner-parent:4.10.0").toGavtc(Type.pom(), null)
                        .toGavtcf(root.resolve("org/l2x6/pom-tuner/pom-tuner-parent/4.10.0/pom-tuner-parent-4.10.0.pom")),
                pt4100.toGavtc(Type.pom(), null)
                        .toGavtcf(root.resolve("org/l2x6/pom-tuner/pom-tuner/4.10.0/pom-tuner-4.10.0.pom")),
                pt4100.toGavtc(Type.jar(), null)
                        .toGavtcf(root.resolve("org/l2x6/pom-tuner/pom-tuner/4.10.0/pom-tuner-4.10.0.jar")),
                pt4100.toGavtc(Type.jar(), "sources")
                        .toGavtcf(root.resolve("org/l2x6/pom-tuner/pom-tuner/4.10.0/pom-tuner-4.10.0-sources.jar")),
                pt4100.toGavtc(Type.jar(), "javadoc")
                        .toGavtcf(root.resolve("org/l2x6/pom-tuner/pom-tuner/4.10.0/pom-tuner-4.10.0-javadoc.jar")));
    }
}
