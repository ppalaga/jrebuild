/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.domino.scm.recipes.location;

import org.l2x6.jrebuild.domino.scm.recipes.BuildRecipe;

public class AddRecipeRequest<T> {

    private final BuildRecipe<T> recipe;
    private final T data;
    private final String groupId;
    private final String artifactId;
    private final String version;

    public AddRecipeRequest(BuildRecipe<T> recipe, T data, String groupId, String artifactId, String version) {
        this.recipe = recipe;
        this.data = data;
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getVersion() {
        return version;
    }

    public BuildRecipe<T> getRecipe() {
        return recipe;
    }

    public T getData() {
        return data;
    }
}
