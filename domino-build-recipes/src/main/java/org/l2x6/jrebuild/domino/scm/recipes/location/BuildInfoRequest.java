/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.domino.scm.recipes.location;

import java.util.Set;
import org.l2x6.jrebuild.domino.scm.recipes.BuildRecipe;

public class BuildInfoRequest {

    private final String scmUri;
    private final String version;
    private final Set<BuildRecipe> recipeFiles;

    public BuildInfoRequest(String scmUri, String version, Set<BuildRecipe> recipeFiles) {
        this.scmUri = scmUri;
        this.version = version;
        this.recipeFiles = recipeFiles;
    }

    public Set<BuildRecipe> getRecipeFiles() {
        return recipeFiles;
    }

    public String getScmUri() {
        return scmUri;
    }

    public String getVersion() {
        return version;
    }
}
