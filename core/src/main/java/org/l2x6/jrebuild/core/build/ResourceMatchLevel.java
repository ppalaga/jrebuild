package org.l2x6.jrebuild.core.build;

import java.util.List;

/**
 *
 */
public enum ResourceMatchLevel implements Comparable<ResourceMatchLevel> {
    /** The resource is available in the rebuild but it is missing in the reference build. */
    MISSING_IN_REFERENCE(Reproducibility.UNBUILDABLE),
    /** The resource is available in the reference build but is missing the rebuild. */
    MISSING_IN_REBUILD(Reproducibility.UNBUILDABLE),
    /**
     * Not fulfilling the criteria for {@link #PERFECT} nor {@link #SUFFICIENT}, but still available in both reference
     * and rebuild.
     */
    BUILDABLE(Reproducibility.BUILDABLE),
    /**
     * <ul>
     * <li>For class files: the file structure (fields, method signatures and constant pool match) is the same, instructions
     * in methods may differ
     * <li>For other resources, some specific mismatches may occur, such as
     * <ul>
     * <li>some specific entries in meta-inf/Manifest.mf, such as date, builder JVM, etc.
     * <li>end of line characters
     * </ul>
     */
    SUFFICIENT(Reproducibility.SUFFICIENT),
    /** Binary equal with the reference resource */
    PERFECT(Reproducibility.PERFECT);

    private final Reproducibility reproducibility;

    ResourceMatchLevel(Reproducibility reproducibility) {
        this.reproducibility = reproducibility;
    }

    /**
     * @param  other the {@link ResourceMatchLevel} to compare this {@link ResourceMatchLevel} against
     * @return       {@code true} if this {@link ResourceMatchLevel} is the same or higher level of match than the specified
     *               {@code other} {@link ResourceMatchLevel} or {@code false} otherwise
     */
    public boolean isHigherOrSame(ResourceMatchLevel other) {
        return this.ordinal() >= other.ordinal();
    }

    /**
     * @param  other the {@link ResourceMatchLevel} to compare this {@link ResourceMatchLevel} against
     * @return       {@code true} if this {@link ResourceMatchLevel} is higher level of match than the specified
     *               {@code other} {@link ResourceMatchLevel} or {@code false} otherwise
     */
    public boolean isHigher(ResourceMatchLevel other) {
        return this.ordinal() > other.ordinal();
    }

    public ResourceMatch match(String path) {
        return new ResourceMatch(this, path, null, List.of());
    }

    public ResourceMatchLevel lower(ResourceMatchLevel other) {
        return this.ordinal() < other.ordinal() ? this : other;
    }

    public Reproducibility reproducibility() {
        return reproducibility;
    }
}
