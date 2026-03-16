package org.l2x6.jrebuild.core.build;

/**
 *
 */
public enum ResourceMatch implements Comparable<ResourceMatch> {
    /** Binary equal with the reference resource */
    PERFECT,
    /**
     * <ul>
     * <li>For class files: the file structure (fields, method signatures and constant pool match) is the same, instructions in methods may differ
     * <li>For other resources, some specific mismatches may occur, such as
     * <ul>
     * <li>some specific entries in meta-inf/Manifest.mf, such as date, builder JVM, etc.
     * <li>end of line characters
     * </ul>
     */
    SUFFICIENT,
    /**
     * Not fulfilling the criteria for {@link #PERFECT} nor {@link #SUFFICIENT}.
     */
    MISMATCH
    ;

    /**
     * @param  requiredMatch the baseline to compare this {@link ResourceMatch} against
     * @return                         {@code true} if this {@link ResourceMatch} is the same or better than the specified
     *                                 {@code requiredMatch} or {@code false} otherwise
     */
    public boolean isBetterOrSame(ResourceMatch requiredMatch) {
        return this.ordinal() >= requiredMatch.ordinal();
    }
}
