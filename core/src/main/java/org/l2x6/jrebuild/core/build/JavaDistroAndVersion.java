package org.l2x6.jrebuild.core.build;

import java.util.Comparator;
import java.util.Optional;
import org.l2x6.jrebuild.api.os.Tool;
import org.l2x6.jrebuild.api.util.ComparableVersion;

public record JavaDistroAndVersion(String distro, ComparableVersion version) implements Comparable<JavaDistroAndVersion> {
    private static final Comparator<JavaDistroAndVersion> COMPARATOR = Comparator.comparing(JavaDistroAndVersion::distro)
            .thenComparing(JavaDistroAndVersion::version, Comparator.reverseOrder());
    public static final String UNKNOWN = "unknown";

    public static JavaDistroAndVersion temurin(ComparableVersion version) {
        return new JavaDistroAndVersion("Temurin", version);
    }

    Optional<Tool> tool() {
        return Optional.of(new Tool("sdkman", "java", version + "-" + distro));
    }

    @Override
    public int compareTo(JavaDistroAndVersion o) {
        return COMPARATOR.compare(this, o);
    }
}
