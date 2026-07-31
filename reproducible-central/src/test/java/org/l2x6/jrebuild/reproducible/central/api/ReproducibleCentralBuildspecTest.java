/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild
 *                                 project contributors as indicated by the @author tags
 *                                 SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.reproducible.central.api;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.lib.Ref;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;
import org.l2x6.jrebuild.reproducible.central.ReproducibleCentralLayout;
import org.l2x6.pom.tuner.model.Gav;

class ReproducibleCentralBuildspecTest {
    private static final Logger log = Logger.getLogger(ReproducibleCentralBuildspecTest.class);

    @Test
    void parseAll() throws IOException, InvalidRemoteException, TransportException, GitAPIException {

        //final String remote = "file:///home/ppalaga/orgs/pnc/reproducible-central/.git";
        final String remote = "https://github.com/jvm-repo-rebuild/reproducible-central.git";
        final String branch = "master";

        Path userHome = Path.of(System.getProperty("user.home"));
        Path cloneDir = userHome.resolve(".m2/buildspec/reproducible-central");
        UUID uuid = UUID.randomUUID();
        if (!Files.exists(cloneDir)) {
            cloneDir = Path.of("target/clone-" + uuid).toAbsolutePath().normalize();
            Files.createDirectories(cloneDir);
        }
        Path cacheDir = Path.of("target/cache-" + uuid).toAbsolutePath().normalize();
        Files.createDirectories(cacheDir);
        Path indexBaseDir = cacheDir.resolve("reproducible-central-index");

        final Set<Gav> gavs1;
        {
            long start = System.currentTimeMillis();
            BuildspecRepository central = ReproducibleCentralLayout.cloneOrFetch(remote, branch, cloneDir, indexBaseDir,
                    cacheDir);
            assertCentral(cloneDir, indexBaseDir, central);
            log.infof("Parsed Reproducible Central Buildspecs in %s" + Duration.ofMillis(System.currentTimeMillis() - start));
            gavs1 = central.gavs();
        }
        final Set<Gav> gavs2;
        {
            long start = System.currentTimeMillis();
            BuildspecRepository central = ReproducibleCentralLayout.cloneOrFetch(remote, branch, cloneDir, indexBaseDir,
                    cacheDir);
            assertCentral(cloneDir, indexBaseDir, central);
            log.infof("Loaded Reproducible Central Buildspecs from properties in %s"
                    + Duration.ofMillis(System.currentTimeMillis() - start));
            gavs2 = central.gavs();
        }

        Assertions.assertThat(gavs1).isEqualTo(gavs2);
    }

    private void assertCentral(Path cloneDir, Path cacheDir, BuildspecRepository central) throws IOException {
        Gav camelCore = Gav.of("org.jreleaser:jreleaser-config-json:1.9.0");
        Buildspec buildspec = central.lookup(camelCore);
        Assertions.assertThat(buildspec.gav()).isEqualTo(Gav.of("org.jreleaser:jreleaser:1.9.0"));
        Assertions.assertThat(buildspec.gitTag()).isEqualTo("v1.9.0");

        try (Git git = Git.open(cloneDir.toFile())) {
            final Ref ref = git.getRepository().exactRef("HEAD");
            String commitId = ref.getObjectId().getName();
            Assertions.assertThat(cacheDir.resolve(commitId)).isDirectory();
        }
    }
}
