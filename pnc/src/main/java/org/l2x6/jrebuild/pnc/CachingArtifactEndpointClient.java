/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild
 *                                 project contributors as indicated by the @author tags
 *                                 SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.pnc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import org.jboss.pnc.dto.Artifact;
import org.jboss.pnc.dto.response.ArtifactInfo;
import org.jboss.pnc.dto.response.Page;
import org.jboss.pnc.enums.ArtifactQuality;
import org.jboss.pnc.enums.BuildCategory;
import org.jboss.pnc.enums.RepositoryType;

public record CachingArtifactEndpointClient(
        Path getSpecificCacheDir,
        Path getAllFilteredCacheDir,
        Instant maxPncBuildDate,
        ObjectMapper mapper,
        ArtifactEndpointClient delegate) implements ArtifactEndpointClient {

    @Override
    public Page<ArtifactInfo> getAllFiltered(
            int pageIndex,
            int pageSize,
            String identifier,
            Set<ArtifactQuality> qualities,
            RepositoryType repoType,
            Set<BuildCategory> buildCategories) {
        return delegate.getAllFiltered(pageIndex, pageSize, identifier, qualities, repoType, buildCategories);
    }

    public Stream<ArtifactInfo> getAllFiltered(String gavPattern) {
        CacheEntry sanitizedGavPattern = CacheEntry.of(getAllFilteredCacheDir, gavPattern);

        Optional<List<ArtifactInfo>> cached = sanitizedGavPattern.<List<ArtifactInfo>> read(
                mapper,
                file -> {
                    try {
                        Instant lastChange = Files.getLastModifiedTime(file).toInstant();
                        return lastChange.isBefore(lastChange);
                    } catch (IOException e) {
                        throw new UncheckedIOException("Could not read last mod time of " + file, e);
                    }
                },
                new TypeReference<List<ArtifactInfo>>() {
                });
        if (cached.isPresent()) {
            return cached.get().stream();
        }

        Function<Integer, Page<ArtifactInfo>> getArtifactPages = i -> {
            //log.debugf("Getting page %d", i);
            Page<ArtifactInfo> result = delegate.getAllFiltered(
                    i,
                    org.jboss.pnc.rest.configuration.Constants.MAX_PAGE_SIZE,
                    gavPattern,
                    Set.of(org.jboss.pnc.enums.ArtifactQuality.NEW),
                    RepositoryType.MAVEN, Set.of(BuildCategory.values()));
            //log.debugf("Got %d results on page %d/%d for %s", result.getTotalHits(), result.getPageIndex(), result.getTotalPages(), gav);
            return result;
        };

        /* Cache locally */
        List<ArtifactInfo> result = Clients.stream(getArtifactPages).toList();
        sanitizedGavPattern.write(mapper, result);
        return result.stream();
    }

    @Override
    public Artifact getSpecific(String id) {
        CacheEntry ce = CacheEntry.of(getSpecificCacheDir, id);
        return ce.<Artifact> read(
                mapper,
                file -> true, // we assume the build data for a specific build is immutable
                new TypeReference<Artifact>() {
                })
                .orElse(delegate.getSpecific(id));
    }
}
