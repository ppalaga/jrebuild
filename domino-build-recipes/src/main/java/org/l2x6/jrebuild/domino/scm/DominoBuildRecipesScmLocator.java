/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.domino.scm;

import java.util.List;
import org.l2x6.jrebuild.api.scm.ScmLocator;
import org.l2x6.jrebuild.api.scm.ScmRef;
import org.l2x6.pom.tuner.model.Gav;

public class DominoBuildRecipesScmLocator implements ScmLocator {
    private final List<String> recipeUris;

    DominoBuildRecipesScmLocator(List<String> recipeUris) {
        super();
        this.recipeUris = recipeUris;
    }

    @Override
    public ScmRef locate(Gav gav) {

        for (String recipeUri : recipeUris) {

        }
        return null;
    }

    static class DominoBuildRecipeRepository {

    }

}
