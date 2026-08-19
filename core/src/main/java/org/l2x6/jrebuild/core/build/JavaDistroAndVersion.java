package org.l2x6.jrebuild.core.build;

import org.l2x6.jrebuild.api.os.Tool;
import org.l2x6.jrebuild.api.util.ComparableVersion;

import java.util.Comparator;
import java.util.Optional;

public record JavaDistroAndVersion(String distro, ComparableVersion version) {
    private static final Comparator<JavaDistroAndVersion> COMPARATOR = Comparator.comparing(BuildToolAndVersion::buildTool).thenComparing(BuildToolAndVersion::version, Comparator.reverseOrder());
    public static final String UNKNOWN = "unknown";

    Optional<Tool> tool() {
        return Optional.of(new Tool("sdkman", "java", version + "-" + distro));
    }
}
