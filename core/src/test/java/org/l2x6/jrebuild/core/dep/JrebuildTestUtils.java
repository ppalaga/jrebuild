/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.dep;

import eu.maveniverse.maven.mima.context.ContextOverrides;
import eu.maveniverse.maven.mima.context.ContextOverrides.AddRepositoriesOp;
import eu.maveniverse.maven.mima.context.ContextOverrides.Builder;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;

public class JrebuildTestUtils {

    public static Builder testRepo() {
        RemoteRepository localRepo = new RemoteRepository.Builder(
                "local-test",
                "default",
                Path.of(".").toAbsolutePath().normalize().resolve("target/repo").toUri().toString())
                .setSnapshotPolicy(new RepositoryPolicy(true, "never", "ignore"))
                .build();
        return ContextOverrides.create()
                .addRepositoriesOp(AddRepositoriesOp.REPLACE)
                .repositories(List.of(localRepo))
                .withLocalRepositoryOverride(Path.of("target/local-maven-repo-" + UUID.randomUUID()));
    }

}
