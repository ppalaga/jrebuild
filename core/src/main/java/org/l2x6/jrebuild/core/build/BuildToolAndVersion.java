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

public record BuildToolAndVersion(
        BuildTool buildTool,
        @JsonSerialize(using = Serializers.ComparableVersionSerializer.class) @JsonDeserialize(
                using = Serializers.ComparableVersionDeserializer.class) ComparableVersion version)
        implements
            Comparable<BuildToolAndVersion> {
    private static final Comparator<BuildToolAndVersion> COMPARATOR = Comparator.comparing(BuildToolAndVersion::buildTool)
            .thenComparing(BuildToolAndVersion::version, Comparator.nullsLast(Comparator.reverseOrder()));

    public Optional<Tool> tool() {
        return buildTool.tool(version == null ? null : version.toString());
    }

    @Override
    public int compareTo(BuildToolAndVersion other) {
        return COMPARATOR.compare(this, other);
    }
}
