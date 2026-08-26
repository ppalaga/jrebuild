/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.build;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.Comparator;
import java.util.Optional;
import org.l2x6.jrebuild.api.os.Tool;
import org.l2x6.jrebuild.api.util.ComparableVersion;
import org.l2x6.jrebuild.core.jackson.Serializers;

public record JavaDistroAndVersion(
        String distro,
        @JsonSerialize(using = Serializers.ComparableVersionSerializer.class) @JsonDeserialize(
                using = Serializers.ComparableVersionDeserializer.class) ComparableVersion version)
        implements
            Comparable<JavaDistroAndVersion> {
    private static final Comparator<JavaDistroAndVersion> COMPARATOR = Comparator.comparing(JavaDistroAndVersion::distro)
            .thenComparing(JavaDistroAndVersion::version, Comparator.reverseOrder());
    public static final String UNKNOWN = "unknown";

    public static JavaDistroAndVersion temurin(ComparableVersion version) {
        return new JavaDistroAndVersion("Temurin", version);
    }

    public static JavaDistroAndVersion temurin(String version) {
        return new JavaDistroAndVersion("Temurin", new ComparableVersion(version));
    }

    Optional<Tool> tool() {
        return Optional.of(new Tool("sdkman", "java", version + "-" + distro));
    }

    @Override
    public int compareTo(JavaDistroAndVersion o) {
        return COMPARATOR.compare(this, o);
    }
}
