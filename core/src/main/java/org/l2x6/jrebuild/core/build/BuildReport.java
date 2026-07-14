package org.l2x6.jrebuild.core.build;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import org.l2x6.pom.tuner.model.Gavtc;

public record BuildReport(
        BuildRequest buildRequest,
        /** When the build was started */
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX") ZonedDateTime buildStart,
        /** How long the build took */
        Duration buildDuration,
        /**
         * Overall reproducibility aggregated over all artifacts
         */
        Reproducibility reproducibility,
        /** Reproducibility status of individual artifacts */
        Map<Gavtc, ResourceMatch> builtArtifacts,
        /** Can be {@code null} */
        String commitId,
        /** Can be {@code null} */
        String errorMessage) {

    private static Comparator<BuildReport> BY_REPRODUCIBILITY_AND_TIMESTAMP_COMPARATOR = Comparator
            .comparing(BuildReport::reproducibility).thenComparing(BuildReport::buildStart);

    public static Comparator<? super BuildReport> byBestReproducibilityAndNewestTimestamp() {
        return BY_REPRODUCIBILITY_AND_TIMESTAMP_COMPARATOR;
    }

    public boolean containsAll(Collection<Gavtc> expectedArtifacts) {
        return builtArtifacts.keySet().containsAll(expectedArtifacts);
    }

}
