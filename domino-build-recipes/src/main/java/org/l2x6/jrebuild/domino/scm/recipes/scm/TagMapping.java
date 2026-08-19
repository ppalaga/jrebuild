/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild
 *                                 project contributors as indicated by the @author tags
 *                                 SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.domino.scm.recipes.scm;

import java.util.Objects;
import java.util.regex.Pattern;

public class TagMapping {

    /**
     * A regex that is matches against a version. Capture groups can be used to capture info that ends up in the tag
     */
    private Pattern pattern;
    /**
     * The corresponding tag, with $n placeholders to represent the capture groups to be replaced
     */
    private String tag;

    public String getPattern() {
        return pattern.pattern();
    }

    public Pattern pattern() {
        return pattern;
    }

    public TagMapping setPattern(String pattern) {
        this.pattern = Pattern.compile(pattern);
        return this;
    }

    public String getTag() {
        return tag;
    }

    public TagMapping setTag(String tag) {
        this.tag = tag;
        return this;
    }

    @Override
    public int hashCode() {
        return Objects.hash(pattern != null ? pattern.pattern() : null, tag);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        TagMapping other = (TagMapping) obj;
        return Objects.equals((pattern != null ? pattern.pattern() : null),
                (other.pattern != null ? other.pattern.pattern() : null)) && Objects.equals(tag, other.tag);
    }

    @Override
    public String toString() {
        return (pattern != null ? pattern.pattern() : null) + " -> " + tag;
    }

}
