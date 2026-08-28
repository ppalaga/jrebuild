/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.build;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;
import org.l2x6.jrebuild.core.jackson.Serializers;
import org.l2x6.pom.tuner.model.Gavtc;
import org.l2x6.pom.tuner.model.OptionalWithDefault;

public record BuildReport(
        BuildRequest buildRequest,
        /** Can be {@code null} */
        String commitId,
        /**
         * Overall reproducibility aggregated over all artifacts
         */
        ReproducibilityOverview reproducibility,
        /** When the build was started */
        ZonedDateTime buildStart,
        /** How long the build took */
        Duration buildDuration,
        /** Reproducibility status of individual artifacts */
        Map<Gavtc, ResourceMatch> builtArtifacts,
        /** Can be {@code null} */
        String errorMessage) {

    private static Comparator<BuildReport> BY_REPRODUCIBILITY_AND_TIMESTAMP_COMPARATOR = Comparator
            .comparing(BuildReport::reproducibility).thenComparing(BuildReport::buildStart);

    public BuildReport(
            BuildRequest buildRequest,
            String commitId,
            ReproducibilityOverview reproducibility,

            @JsonFormat(shape = JsonFormat.Shape.STRING,
                    pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSSXXX") ZonedDateTime buildStart,

            Duration buildDuration,

            @JsonSerialize(keyUsing = Serializers.GavtcKeySerializer.class) @JsonDeserialize(
                    using = Serializers.GavtcTreeMapDeserializer.class) Map<Gavtc, ResourceMatch> builtArtifacts,

            String errorMessage) {

        this.buildRequest = buildRequest;
        this.commitId = commitId;
        this.reproducibility = reproducibility;
        this.buildStart = buildStart;
        this.buildDuration = buildDuration;
        this.builtArtifacts = builtArtifacts == null ? Map.of() : builtArtifacts;
        this.errorMessage = errorMessage;
    }

    public static Comparator<? super BuildReport> byBestReproducibilityAndNewestTimestamp() {
        return BY_REPRODUCIBILITY_AND_TIMESTAMP_COMPARATOR;
    }

    public boolean containsAll(Collection<Gavtc> expectedArtifacts) {
        return builtArtifacts.keySet().containsAll(expectedArtifacts);
    }

    public record ReproducibilityOverview(
            Reproducibility overall,
            Reproducibility poms,
            Reproducibility jars,
            Reproducibility sources,
            Reproducibility javadocs,
            Reproducibility others) implements Comparable<ReproducibilityOverview> {

        public static final ReproducibilityOverview PERFECT = new ReproducibilityOverview(Reproducibility.PERFECT,
                Reproducibility.PERFECT, Reproducibility.PERFECT, Reproducibility.PERFECT, Reproducibility.PERFECT,
                Reproducibility.PERFECT);
        public static final ReproducibilityOverview INVALID_SOURCE_INFO = new ReproducibilityOverview(
                Reproducibility.INVALID_SOURCE_INFO, Reproducibility.INVALID_SOURCE_INFO, Reproducibility.INVALID_SOURCE_INFO,
                Reproducibility.INVALID_SOURCE_INFO, Reproducibility.INVALID_SOURCE_INFO, Reproducibility.INVALID_SOURCE_INFO);
        public static final ReproducibilityOverview UNBUILDABLE = new ReproducibilityOverview(Reproducibility.UNBUILDABLE,
                Reproducibility.UNBUILDABLE, Reproducibility.UNBUILDABLE, Reproducibility.UNBUILDABLE,
                Reproducibility.UNBUILDABLE, Reproducibility.UNBUILDABLE);
        public static final ReproducibilityOverview FAILED = new ReproducibilityOverview(Reproducibility.FAILED,
                Reproducibility.FAILED, Reproducibility.FAILED, Reproducibility.FAILED, Reproducibility.FAILED,
                Reproducibility.FAILED);

        public static ReproducibilityOverview of(Map<Gavtc, ResourceMatch> builtArtifactsMap) {
            if (builtArtifactsMap == null || builtArtifactsMap.isEmpty()) {
                throw new IllegalStateException("builtArtifactsMap must not be empty or null");
            }
            EnumMap<ReproducibilityGroup, ResourceMatchLevel> m = new EnumMap<>(ReproducibilityGroup.class);
            builtArtifactsMap.entrySet().stream()
                    .forEach(en -> {
                        ReproducibilityGroup k = ReproducibilityGroup.of(en.getKey());
                        m.compute(k, (kk, vv) -> en.getValue().level().lower(vv));
                    });
            return new ReproducibilityOverview(
                    m.values().stream().min(Comparator.comparing(ResourceMatchLevel::ordinal))
                            .orElseThrow()
                            .reproducibility(),
                    reproducibility(m, ReproducibilityGroup.poms),
                    reproducibility(m, ReproducibilityGroup.jars),
                    reproducibility(m, ReproducibilityGroup.sources),
                    reproducibility(m, ReproducibilityGroup.javadocs),
                    reproducibility(m, ReproducibilityGroup.others));
        }

        static Reproducibility reproducibility(EnumMap<ReproducibilityGroup, ResourceMatchLevel> m, ReproducibilityGroup g) {
            ResourceMatchLevel val = m.get(g);
            if (val != null) {
                return val.reproducibility();
            }
            return null;
        }

        @Override
        public int compareTo(ReproducibilityOverview o) {
            return overall.compareTo(o.overall);
        }

        public String toFormatedString(String indent) {
            StringJoiner sb = new StringJoiner("\n");
            toMap().entrySet().stream()
                    .filter(en -> en.getValue() != null)
                    .map(en -> indent + en.getKey() + ": " + en.getValue())
                    .forEach(sb::add);
            sb.add(indent + "-------");
            sb.add(indent + "overall: " + overall);
            return sb.toString();
        }

        Map<ReproducibilityGroup, Reproducibility> toMap() {
            EnumMap<ReproducibilityGroup, Reproducibility> m = new EnumMap<>(ReproducibilityGroup.class);
            m.put(ReproducibilityGroup.poms, poms);
            m.put(ReproducibilityGroup.jars, jars);
            m.put(ReproducibilityGroup.sources, sources);
            m.put(ReproducibilityGroup.javadocs, javadocs);
            m.put(ReproducibilityGroup.others, others);
            return Collections.unmodifiableMap(m);
        }

        enum ReproducibilityGroup {
            poms, jars, sources, javadocs, others;

            static ReproducibilityGroup of(Gavtc gavtc) {
                String classifier = gavtc.getClassifier();
                OptionalWithDefault type = gavtc.getType();
                if (classifier == null || classifier.isEmpty()) {
                    if (Gavtc.Type.pom().equals(type)) {
                        return poms;
                    } else if (Gavtc.Type.jar().equals(type)) {
                        return jars;
                    } else {
                        return others;
                    }
                } else if (Gavtc.Type.jar().equals(type)) {
                    if ("javadoc".equals(classifier)) {
                        return javadocs;
                    } else if ("sources".equals(classifier)) {
                        return sources;
                    }
                }
                return others;
            }
        }
    }
}
