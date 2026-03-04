package org.l2x6.jrebuild.core.build;

public enum Reproducibility implements Comparable<Reproducibility> {
    /** Binary equal with reference artifacts on Central */
    PERFECT,
    /**
     * <ul>
     * <li>All artifacts available
     * <li>Lists of files in archives are the same
     * <li>Class file structure (fields, method signatures and constant pool match) of all classes is the same same,
     * the following mismatches may occur:
     * <ul>
     * <li>Archive entry date mismatch
     * <li>meta-inf/Manifest.mf entries mismatch: date
     * </ul>
     */
    SUFFICIENT,
    /**
     * <ul>
     * <li>Build command succeeded
     * <li>All artifacts available
     * </ul>
     */
    BUILDABLE,
    /** Anything else that does not fulfill the criteria for {@link #PERFECT}, {@link #SUFFICIENT} nor {@link #BUILDABLE} */
    UNBUILDABLE;

    /**
     * @param  requiredReproducibility the baseline to compare this {@link Reproducibility} against
     * @return                         {@code true} if this {@link Reproducibility} is the same or better than the specified
     *                                 {@code requiredReproducibility} or {@code false} otherwise
     */
    public boolean isBetterOrSame(Reproducibility requiredReproducibility) {
        return this.ordinal() >= requiredReproducibility.ordinal();
    }
}
