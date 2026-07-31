/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild
 *                                 project contributors as indicated by the @author tags
 *                                 SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.maven;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.core.file.FileSystem;
import io.vertx.mutiny.ext.auth.prng.VertxContextPRNG;
import java.nio.file.Path;
import java.util.Objects;
import org.jboss.logging.Logger;

public class DeployDirectoriesLayout {
    private static final Logger log = Logger.getLogger(DeployDirectoriesLayout.class);
    private final Path deployRootDirectory;
    private final FileSystem fileSystem;
    private final VertxContextPRNG prng;

    public DeployDirectoriesLayout(Path clonesRootDirectory, Vertx vertx) {
        super();
        this.deployRootDirectory = Objects.requireNonNull(clonesRootDirectory, "clonesRootDirectory").toAbsolutePath()
                .normalize();
        this.fileSystem = Objects.requireNonNull(vertx, "vertx").fileSystem();
        this.prng = VertxContextPRNG.current(vertx);
    }

    @SuppressWarnings("unused")
    public Uni<DeployDirectory> createDeployDirectory() {
        final Path result = deployRootDirectory.resolve("deploy-" + prng.nextString(32)).toAbsolutePath().normalize();
        return fileSystem.mkdirs(result.toString()).map(v -> new DeployDirectory(result));
    }

    public class DeployDirectory {
        private final Path deployDirectory;

        public DeployDirectory(Path lockedDirectory) {
            super();
            this.deployDirectory = lockedDirectory;
        }

        public Path deployDirectory() {
            return deployDirectory;
        }

        public Uni<Void> close() {
            return fileSystem
                    .deleteRecursive(deployDirectory.toString())
                    .chain(v -> Uni.createFrom()
                            .item(() -> {
                                prng.close();
                                return null;
                            }));
        }

    }
}
