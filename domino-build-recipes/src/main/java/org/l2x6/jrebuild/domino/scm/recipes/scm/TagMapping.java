/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.domino.scm.recipes.scm;

public class TagMapping {

    /**
     * A regex that is matches against a version. Capture groups can be used to capture info that ends up in the tag
     */
    private String pattern;
    /**
     * The corresponding tag, with $n placeholders to represent the capture groups to be replaced
     */
    private String tag;

    public String getPattern() {
        return pattern;
    }

    public TagMapping setPattern(String pattern) {
        this.pattern = pattern;
        return this;
    }

    public String getTag() {
        return tag;
    }

    public TagMapping setTag(String tag) {
        this.tag = tag;
        return this;
    }
}
