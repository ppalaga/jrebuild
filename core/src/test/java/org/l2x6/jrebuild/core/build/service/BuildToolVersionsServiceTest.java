/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild
 *                                 project contributors as indicated by the @author tags
 *                                 SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.build.service;

import io.vertx.mutiny.core.Vertx;
import java.time.ZonedDateTime;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.l2x6.jrebuild.core.build.BuildTool;

public class BuildToolVersionsServiceTest {
    @Test
    void e2e() {
        Vertx vertx = null;
        try {
            vertx = Vertx.vertx();
            BuildToolVersionsService versionsService = new BuildToolVersionsService(vertx);

            {
                String version = versionsService
                        .latestVersionAsOf(BuildTool.maven, ZonedDateTime.parse("2024-07-01T00:00:00.000000000Z"))
                        .await().indefinitely();

                Assertions.assertThat(version).isEqualTo("3.9.8");

                Map<ZonedDateTime, String> versions = versionsService.versionsByDate(BuildTool.maven).await().indefinitely();

                Assertions.assertThat(versions.get(ZonedDateTime.parse("2025-12-13T09:18Z"))).isEqualTo("3.9.12");
                Assertions.assertThat(versions.get(ZonedDateTime.parse("2025-07-12T18:32Z"))).isEqualTo("3.9.11");

            }

            {
                String version = versionsService
                        .latestVersionAsOf(BuildTool.gradle, ZonedDateTime.parse("2024-07-01T00:00:00.000000000Z"))
                        .await().indefinitely();

                Assertions.assertThat(version).isEqualTo("8.9-rc-1");

                Map<ZonedDateTime, String> versions = versionsService.versionsByDate(BuildTool.gradle).await().indefinitely();

                Assertions.assertThat(versions.get(ZonedDateTime.parse("2025-07-31T16:35:12.000000000Z"))).isEqualTo("9.0.0");

            }

        } finally {
            if (vertx != null) {
                vertx.close();
            }
        }
    }
}
