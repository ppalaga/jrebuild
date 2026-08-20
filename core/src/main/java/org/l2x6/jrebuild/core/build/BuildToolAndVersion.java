package org.l2x6.jrebuild.core.build;

import java.util.Comparator;
import java.util.Optional;
import org.l2x6.jrebuild.api.os.Tool;
import org.l2x6.jrebuild.api.util.ComparableVersion;

public record BuildToolAndVersion(BuildTool buildTool, ComparableVersion version) implements Comparable<BuildToolAndVersion> {
    private static final Comparator<BuildToolAndVersion> COMPARATOR = Comparator.comparing(BuildToolAndVersion::buildTool)
            .thenComparing(BuildToolAndVersion::version, Comparator.nullsLast(Comparator.reverseOrder()));

    public Optional<Tool> tool() {
        return buildTool.tool(version.toString());
    }

    @Override
    public int compareTo(BuildToolAndVersion other) {
        return COMPARATOR.compare(this, other);
    }
}
