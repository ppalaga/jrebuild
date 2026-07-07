package org.l2x6.jrebuild.core.build;

/**
 * A Reproducibility level of an artifact, such as jar file or pom.xml file that was rebuilt locally,
 * compared to a reference artifact, typically on Maven Central.
 */
public enum Reproducibility implements Comparable<Reproducibility> {
    /** All expected rebuilt artifacts are binary equal with reference artifacts */
    PERFECT,
    /**
     * <ul>
     * <li>All expected artifacts were rebuilt
     * <li>Lists of files in archives are the same
     * <li>Class file structure (fields, method signatures and constant pool match, regardless of the ordering) of all
     * classes is the same same,
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
     * <li>All artifacts from the build tree that were also deployed to Maven Central can be built and deployed to a
     * local deployment
     * </ul>
     */
    BUILDABLE,
    /**
     * <ul>
     * <li>The source information is complete
     * <li>The build command (guessed or provided from somewhere) does not qualify the built artifacts for
     * {@link #BUILDABLE}
     * </ul>
     */
    UNBUILDABLE,
    /**
     * It is not possible to locate or access the sources
     * <ul>
     * <li>SCM repo URI might be missing
     * <li>SCM repo URI might not be accessible
     * <li>Tag or other kind of reference is unknown or not available in any of the SCM repo URIs
     * </ul>
     */
    INVALID_SOURCE_INFO,
    /**
     * Uncategorized failure happened during checkout, build or comparison
     */
    FAILED;

    /**
     * @param  requiredReproducibility the baseline to compare this {@link Reproducibility} against
     * @return                         {@code true} if this {@link Reproducibility} is the same or better than the specified
     *                                 {@code requiredReproducibility} or {@code false} otherwise
     */
    public boolean isBetterOrSame(Reproducibility requiredReproducibility) {
        return this.ordinal() >= requiredReproducibility.ordinal();
    }
}
