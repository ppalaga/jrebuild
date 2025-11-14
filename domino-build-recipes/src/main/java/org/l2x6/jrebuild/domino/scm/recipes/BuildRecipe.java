/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.domino.scm.recipes;

import java.util.Objects;
import org.l2x6.jrebuild.domino.scm.recipes.build.BuildRecipeInfo;
import org.l2x6.jrebuild.domino.scm.recipes.build.BuildRecipeInfoManager;
import org.l2x6.jrebuild.domino.scm.recipes.scm.ScmInfo;
import org.l2x6.jrebuild.domino.scm.recipes.scm.ScmInfoManager;

/**
 * Represents a recipe file (e.g. scm.yaml) that contains build information
 * <br/>
 * This is not an enum to allow for extensibility
 */
public class BuildRecipe<T> {

    public static final BuildRecipe<ScmInfo> SCM = new BuildRecipe<>("scm.yaml", new ScmInfoManager());
    public static final BuildRecipe<BuildRecipeInfo> BUILD = new BuildRecipe<>("build.yaml", new BuildRecipeInfoManager());

    final String name;
    final RecipeManager<T> handler;

    public BuildRecipe(String name, RecipeManager<T> handler) {
        this.name = name;
        this.handler = handler;
    }

    public String getName() {
        return name;
    }

    public RecipeManager<T> getHandler() {
        return handler;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        BuildRecipe that = (BuildRecipe) o;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
