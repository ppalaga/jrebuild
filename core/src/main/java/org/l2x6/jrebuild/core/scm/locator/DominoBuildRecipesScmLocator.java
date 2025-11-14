/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.scm.locator;

import java.util.List;
import java.util.function.Function;
import org.apache.maven.model.Model;
import org.apache.maven.model.Scm;
import org.l2x6.jrebuild.core.scm.ScmLocator;
import org.l2x6.jrebuild.core.scm.ScmRef;
import org.l2x6.jrebuild.core.scm.ScmRepository;
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

    }

    static class DominoBuildRecipeRepository {

    }

}
