/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.domino.scm.recipes.location;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RecipeLayoutManagerTest {
    @Test
    public void testPaths(@TempDir Path tempDir) {
        RecipeLayoutManager recipeLayoutManager = new RecipeLayoutManager(tempDir);

        recipeLayoutManager.getAllRepositoryPaths();
    }
}
