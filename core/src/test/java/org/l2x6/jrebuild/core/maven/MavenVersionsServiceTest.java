/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild
 *                                 project contributors as indicated by the @author tags
 *                                 SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.maven;

import io.vertx.mutiny.core.Vertx;
import java.time.ZonedDateTime;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class MavenVersionsServiceTest {
    @Test
    void e2e() {
        Vertx vertx = null;
        try {
            vertx = Vertx.vertx();
            MavenVersionsService versionsService = new MavenVersionsService(vertx);

            String version = versionsService.findLatestAtDate(ZonedDateTime.parse("2024-07-01T00:00:00.000000000Z"))
                    .await().indefinitely();

            Assertions.assertThat(version).isEqualTo("3.9.8");

            Map<ZonedDateTime, String> versions = versionsService.versionsByDate().await().indefinitely();

            Assertions.assertThat(versions.get(ZonedDateTime.parse("2025-12-13T09:18Z"))).isEqualTo("3.9.12");
            Assertions.assertThat(versions.get(ZonedDateTime.parse("2025-07-12T18:32Z"))).isEqualTo("3.9.11");

        } finally {
            if (vertx != null) {
                vertx.close();
            }
        }
    }
}
