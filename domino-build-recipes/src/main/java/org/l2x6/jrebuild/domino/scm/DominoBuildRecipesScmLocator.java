/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.domino.scm;

import java.nio.file.Path;
import java.util.List;
import org.l2x6.jrebuild.api.scm.ScmLocator;
import org.l2x6.jrebuild.api.scm.ScmRef;
import org.l2x6.jrebuild.api.scm.ScmRef.Kind;
import org.l2x6.jrebuild.api.scm.ScmRepository;
import org.l2x6.jrebuild.domino.scm.recipes.scm.GitScmLocator;
import org.l2x6.jrebuild.domino.scm.recipes.scm.RepositoryInfo;
import org.l2x6.jrebuild.domino.scm.recipes.scm.TagInfo;
import org.l2x6.pom.tuner.model.Gav;

public class DominoBuildRecipesScmLocator implements ScmLocator {
    private final GitScmLocator recipeUris;

    DominoBuildRecipesScmLocator(Path cloneDir, List<String> recipeUris) {
        super();
        this.recipeUris = GitScmLocator.builder()
                .setGitCloneBaseDir(cloneDir)
                .setRecipeRepos(recipeUris)
                .build();
    }

    @Override
    public ScmRef locate(Gav gav) {
        TagInfo tagInfo = recipeUris.resolveTagInfo(gav);
        RepositoryInfo repoInfo = tagInfo.getRepoInfo();
        return new ScmRef(tagInfo.getTag(), Kind.TAG, new ScmRepository(repoInfo.getType(), repoInfo.getUriWithoutFragment()));
    }

}
