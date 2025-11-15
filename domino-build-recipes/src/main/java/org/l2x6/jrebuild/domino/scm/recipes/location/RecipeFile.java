/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.domino.scm.recipes.location;

import java.nio.file.Path;

public record RecipeFile(Priority priority, Path recipeFile) implements Comparable<RecipeFile> {
    public enum Priority {
        GAV,
        GA,
        GV,
        G
    }

    @Override
    public int compareTo(RecipeFile o) {
        return Integer.compare(this.priority.ordinal(), o.priority.ordinal());
    }
}
