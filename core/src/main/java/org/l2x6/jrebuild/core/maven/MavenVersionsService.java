/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild
 *                                 project contributors as indicated by the @author tags
 *                                 SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.maven;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.ext.web.client.WebClient;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.l2x6.jrebuild.core.maven.ReferenceMavenRepository.HttpStatusException;

public class MavenVersionsService {
    private static final Pattern VERSION_PATTERN = Pattern
            .compile("<a href=\"([^/]+)/\"[^>]*>[^<]*</a>\\s+(\\d{4})-(\\d{2})-(\\d{2}) (\\d{2}):(\\d{2})");

    private final Uni<Map<ZonedDateTime, String>> versionsByDate;

    public MavenVersionsService(Vertx vertx) {
        super();
        String url = "https://repo1.maven.org/maven2/org/apache/maven/maven-core/";
        this.versionsByDate = Uni.createFrom().item(() -> WebClient.create(vertx))
                .chain(client -> client.getAbs(url)
                        .send()
                        .chain(resp -> {
                            if (resp.statusCode() != 200) {
                                return Uni.createFrom().failure(new HttpStatusException(resp.statusCode(),
                                        "Failed to download " + url + ": HTTP " + resp.statusCode()));
                            }
                            return Uni.createFrom().item(versionsByDate(resp.bodyAsString()));
                        })
                        .eventually(client::close))
                .memoize().forFixedDuration(Duration.ofDays(1));
    }

    /**
     * @return for testing
     */
    Uni<Map<ZonedDateTime, String>> versionsByDate() {
        return versionsByDate;
    }

    static Map<ZonedDateTime, String> versionsByDate(String body) {
        Map<ZonedDateTime, String> result = new TreeMap<>(Comparator.reverseOrder());
        Matcher m = VERSION_PATTERN.matcher(body);
        while (m.find()) {
            final String version = m.group(1);
            if (!version.contains("alpha")
                    && !version.contains("beta")
                    && !version.contains("rc"))
                result.put(ZonedDateTime.of(
                        parseInt(m, 2), parseInt(m, 3), parseInt(m, 4), parseInt(m, 5), parseInt(m, 6), 0, 0, ZoneOffset.UTC),
                        version);
        }
        return Collections.unmodifiableMap(result);
    }

    static int parseInt(Matcher m, int group) {
        return Integer.parseInt(m.group(group));
    }

    public Uni<String> findLatestAtDate(ZonedDateTime date) {
        return versionsByDate.map(map -> {
            for (Entry<ZonedDateTime, String> en : map.entrySet()) {
                if (date.isAfter(en.getKey())) {
                    return en.getValue();
                }
            }
            return null;
        });
    }
}
