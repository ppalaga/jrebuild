package org.l2x6.jrebuild.core.build;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.Map;
import org.l2x6.pom.tuner.model.Gavtc;

public record BuildReport(
        BuildRequest buildRequest,
        /** When the build was started */
        ZonedDateTime timeStamp,
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
            .comparing(BuildReport::reproducibility).thenComparing(BuildReport::timeStamp);

    public static Comparator<? super BuildReport> byBestReproducibilityAndNewestTimestamp() {
        return BY_REPRODUCIBILITY_AND_TIMESTAMP_COMPARATOR;
    }

}
