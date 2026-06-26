/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.pnc;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.jboss.logging.Logger;
import org.jboss.pnc.dto.Artifact;
import org.jboss.pnc.dto.Build;
import org.jboss.pnc.dto.response.ArtifactInfo;
import org.l2x6.jrebuild.api.scm.FqScmRef;
import org.l2x6.jrebuild.api.scm.RemoteScmLookup;
import org.l2x6.jrebuild.api.scm.ScmRepository;
import org.l2x6.jrebuild.api.util.ComparableVersion;
import org.l2x6.jrebuild.common.scm.AbstractScmLocator;
import org.l2x6.pom.tuner.model.Gav;
import org.l2x6.pom.tuner.model.Gavtc;
import org.l2x6.pom.tuner.model.Gavtc.Type;

public class PncScmLocator extends AbstractScmLocator {
    private static final Logger log = Logger.getLogger(PncScmLocator.class);
    private static final String SOURCE = "♖";
    private final boolean includeTemporaryVersions;
    private final CachingArtifactEndpointClient artifactEndpoint;
    private final Map<Gav, List<FqScmRef>> cache = new ConcurrentHashMap<>();

    public PncScmLocator(
            Path cacheDir,
            Instant maxPncBuildDate,
            String pncBaseUri,
            boolean includeTemporaryVersions,
            RemoteScmLookup scmLookup) {
        super(scmLookup);
        Objects.requireNonNull(maxPncBuildDate);
        this.includeTemporaryVersions = includeTemporaryVersions;
        final String artifactsUri = pncBaseUri.endsWith("/") ? (pncBaseUri + "artifacts/") : (pncBaseUri + "/artifacts/");
        final Path artifactsCacheDir = cacheDir.resolve("pnc/artifacts");
        final Path getSpecificCacheDir = artifactsCacheDir.resolve("getSpecific");
        final Path getAllFilteredCacheDir = artifactsCacheDir.resolve("getAllFiltered");
        try {
            Files.createDirectories(getSpecificCacheDir);
            Files.createDirectories(getAllFilteredCacheDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create " + artifactsCacheDir, e);
        }

        final CachingResponseFilter filter = new CachingResponseFilter(
                uri -> {
                    String uriStr = uri.toString();
                    return uriStr.startsWith(artifactsUri) && !uriStr.contains("artifacts/filter");
                },
                uri -> CacheEntry.of(getAllFilteredCacheDir, uri.toString().substring(artifactsUri.length())));

        ArtifactEndpointClient client = QuarkusRestClientBuilder.newBuilder()
                .baseUri(URI.create(pncBaseUri))
                .register(filter)
                .build(ArtifactEndpointClient.class);

        ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.artifactEndpoint = new CachingArtifactEndpointClient(
                getSpecificCacheDir,
                getAllFilteredCacheDir,
                maxPncBuildDate,
                mapper,
                client);
    }

    static String toGatvc(Gavtc gav) {
        String type = gav.getType().getValueOrDefault();
        StringBuilder sb = new StringBuilder();
        gav.toGa().toString(sb)
                .append(":").append(type)
                .append(":").append(normalizeVersion(gav.getVersion()) + "*");
        String classifier = gav.getClassifier();
        if (classifier != null) {
            sb.append(":").append(classifier);
        }
        return sb.toString();
    }

    static String normalizeVersion(String version) {
        String[] segments = version.split("\\.");
        switch (segments.length) {
        case 1: {
            return version + ".0.0";
        }
        case 2: {
            return version + ".0";
        }
        default:
            return version;
        }
    }

    static String toPomGatv(Gav gav) {
        return gav.toGa().toString() + ":pom:" + normalizeVersion(gav.getVersion()) + "*";
    }

    public String latestPncVersion(Gavtc gav) {
        return latestBuiltArtifact(gav)
                .map(a -> a.version().toString())
                .orElse(null);
    }

    public List<FqScmRef> locate(Gav gav) {
        return cache.computeIfAbsent(gav, k -> {
            Gavtc pomGav = k.toGavtc(Type.pom(), null);
            Optional<ComparableArtifactInfo> latestBuiltArtifact = latestBuiltArtifact(pomGav);
            if (latestBuiltArtifact.isPresent()) {
                ArtifactInfo latestArtifactInfo = latestBuiltArtifact.get().artifact();
                log.debugf("Latest build %s", latestArtifactInfo.getIdentifier());
                Artifact artifact = artifactEndpoint.getSpecific(latestArtifactInfo.getId());
                Build build = artifact.getBuild();
                String externalUrl;
                if (build != null && (externalUrl = build.getScmRepository().getExternalUrl()) != null) {
                    externalUrl = normalizeScmUri(externalUrl);
                    ScmRepository repo = new ScmRepository(SOURCE, "git", externalUrl);
                    String tag = build.getBuildConfigRevision().getScmRevision();
                    log.debugf("Validating tag from PNC for %s: %s#%s", latestArtifactInfo.getIdentifier(), repo, tag);
                    FqScmRef ref = validateTag(repo, tag, k.getVersion());
                    return List.of(ref);
                }
            }
            return List.of();
        });
    }

    private Optional<ComparableArtifactInfo> latestBuiltArtifact(Gavtc gav) {
        Stream<ComparableArtifactInfo> stream = artifactEndpoint.getAllFiltered(toGatvc(gav))
                .map(ComparableArtifactInfo::of);
        if (!includeTemporaryVersions) {
            stream = stream.filter(artifactInfo -> !artifactInfo.version().toString().contains("temporary-"));
        }
        return stream
                //.peek(ai -> log.debugf("PNC artifact %s", ai.artifact.getIdentifier()))
                .max(Comparator.comparing(ComparableArtifactInfo::version));
    }

    static record ComparableArtifactInfo(ArtifactInfo artifact, ComparableVersion version) {
        static ComparableArtifactInfo of(ArtifactInfo artifact) {
            Gatvc gatv = Gatvc.of(artifact.getIdentifier());
            ComparableVersion v = new ComparableVersion(gatv.gav.getVersion());
            return new ComparableArtifactInfo(artifact, v);
        }
    }

    static record Gatvc(Gav gav, String type, String classifier) {
        public static Gatvc of(String gatv) {
            String[] segments = gatv.split(":");
            switch (segments.length) {
            case 4: {
                return new Gatvc(new Gav(segments[0], segments[1], segments[3]), segments[2], null);
            }
            case 5: {
                return new Gatvc(new Gav(segments[0], segments[1], segments[3]), segments[2],
                        segments[4].isEmpty() ? null : segments[4]);
            }
            default:
                throw new IllegalArgumentException("Expected 4 or 5 segments, found " + gatv);
            }
        }

        public String toString() {
            StringBuilder sb = gav.toGa().toString(new StringBuilder()).append(":").append(type).append(":")
                    .append(gav.getVersion());
            if (classifier != null) {
                sb.append(":").append(classifier);
            }
            return sb.toString();
        }
    }

}
