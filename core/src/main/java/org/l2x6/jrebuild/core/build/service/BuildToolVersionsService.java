/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.build.service;

import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.ext.web.client.WebClient;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.l2x6.jrebuild.core.build.BuildTool;
import org.l2x6.jrebuild.core.maven.ReferenceMavenRepository.HttpStatusException;

public class BuildToolVersionsService {
    private static final Pattern MAVEN_CENTRAL_VERSION_PATTERN = Pattern
            .compile("<a href=\"([^/]+)/\"[^>]*>[^<]*</a>\\s+(\\d{4})-(\\d{2})-(\\d{2}) (\\d{2}):(\\d{2})");
    private static final Pattern ANT_VERSION_PATTERN = Pattern
            .compile("<a href=\"apache-ant-([^\"]+)-bin.zip\"[^>]*>[^<]*</a>\\s+(\\d{4})-(\\d{2})-(\\d{2}) (\\d{2}):(\\d{2})");
    private static final DateTimeFormatter GRADLE_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssZ");

    private final Map<BuildTool, Uni<Map<ZonedDateTime, String>>> versionsByDate;

    public BuildToolVersionsService(Vertx vertx) {
        super();
        this.versionsByDate = Map.of(
                BuildTool.maven,
                getVersionsByDate(vertx, "https://repo1.maven.org/maven2/org/apache/maven/maven-core/",
                        MAVEN_CENTRAL_VERSION_PATTERN),
                BuildTool.gradle, getGradleVersionsByDate(vertx),
                BuildTool.ant, getVersionsByDate(vertx, "https://archive.apache.org/dist/ant/binaries/", ANT_VERSION_PATTERN));
    }

    static Uni<Map<ZonedDateTime, String>> getGradleVersionsByDate(Vertx vertx) {
        return Uni.createFrom().item(() -> WebClient.create(vertx))
                .chain(client -> getGradleVersionsByDate(client, 0, new TreeMap<>(Comparator.reverseOrder()))
                        .eventually(client::close))
                .memoize().forFixedDuration(Duration.ofDays(1));
    }

    static Uni<Map<ZonedDateTime, String>> getGradleVersionsByDate(WebClient client, int major,
            Map<ZonedDateTime, String> result) {
        String url = "https://services.gradle.org/versions/" + major;
        return client.getAbs(url)
                .send()
                .chain(resp -> {
                    if (resp.statusCode() == 404) {
                        /* No such major version - time to terminate */
                        return Uni.createFrom().item(result);
                    }
                    if (resp.statusCode() != 200) {
                        return Uni.createFrom().failure(new HttpStatusException(resp.statusCode(),
                                "Failed to download " + url + ": HTTP " + resp.statusCode()));
                    }
                    return Uni.createFrom().item(parseGradleVersions(resp.bodyAsJsonArray(), result))
                            .chain(r -> getGradleVersionsByDate(client, major + 1, result));
                });
    }

    static Uni<Map<ZonedDateTime, String>> parseGradleVersions(JsonArray body, Map<ZonedDateTime, String> result) {
        body.stream()
                .map(o -> (JsonObject) o)
                .forEach(o -> result.put(
                        ZonedDateTime.parse(o.getString("buildTime"), GRADLE_DATE_FORMATTER),
                        o.getString("version")));
        return Uni.createFrom().item(result);
    }

    static Uni<Map<ZonedDateTime, String>> getVersionsByDate(Vertx vertx, String url, Pattern versionPattern) {
        return Uni.createFrom().item(() -> WebClient.create(vertx))
                .chain(client -> client.getAbs(url)
                        .send()
                        .chain(resp -> {
                            if (resp.statusCode() != 200) {
                                return Uni.createFrom().failure(new HttpStatusException(resp.statusCode(),
                                        "Failed to download " + url + ": HTTP " + resp.statusCode()));
                            }
                            return Uni.createFrom().item(versionsByDate(resp.bodyAsString(), versionPattern));
                        })
                        .eventually(client::close))
                .memoize().forFixedDuration(Duration.ofDays(1));
    }

    /**
     * @return for testing
     */
    Uni<Map<ZonedDateTime, String>> versionsByDate(BuildTool buildTool) {
        return versionsByDate.get(buildTool);
    }

    static Map<ZonedDateTime, String> versionsByDate(String body, Pattern versionPattern) {
        Map<ZonedDateTime, String> result = new TreeMap<>(Comparator.reverseOrder());
        Matcher m = versionPattern.matcher(body);
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

    public Uni<String> latestVersionAsOf(BuildTool buildTool, ZonedDateTime date) {
        if (buildTool.isWrapper()) {
            /* Wrappers do not need a version */
            return Uni.createFrom().nullItem();
        }
        Uni<Map<ZonedDateTime, String>> buildToolUni = versionsByDate.get(buildTool);
        if (buildToolUni == null) {
            return Uni.createFrom().failure(() -> new IllegalStateException(
                    "BuildTool " + buildTool + " is not supported by " + BuildToolVersionsService.class.getSimpleName()));
        }
        return buildToolUni.map(map -> {
            for (Entry<ZonedDateTime, String> en : map.entrySet()) {
                if (date.isAfter(en.getKey())) {
                    return en.getValue();
                }
            }
            throw new IllegalStateException("No versions cached for BuildTool " + buildTool);

        });
    }
}
