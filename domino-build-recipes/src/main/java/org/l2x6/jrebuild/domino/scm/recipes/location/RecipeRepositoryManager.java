/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.domino.scm.recipes.location;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.l2x6.jrebuild.common.git.GitUtils;
import org.l2x6.pom.tuner.model.Gav;

/**
 * A recipe database stored in git.
 */
public class RecipeRepositoryManager implements RecipeDirectory {
    private static AtomicInteger threadIndex = new AtomicInteger();
    private final RecipeLayoutManager recipeLayoutManager;

    public static RecipeDirectory create(String remote, Path directory) throws GitAPIException, IOException {
        // Allow cloning of another branch via <url>#<branch> format.
        String branch = "main";
        int b = remote.indexOf('#');
        if (b > 0) {
            branch = remote.substring(b + 1);
            remote = remote.substring(0, b);
        }
        return create(remote, branch, directory);
    }

    public static RecipeDirectory create(
            String remote,
            String branch,
            Path directory) throws GitAPIException {

        final CompletableFuture<RecipeDirectory> delegate = new CompletableFuture<>();
        new Thread(() -> {
            try {
                try (Git git = GitUtils.cloneOrFetchAndReset(remote, branch, directory, 1)) {
                }
                delegate.complete(new RecipeRepositoryManager(directory));
            } catch (Exception e) {
                delegate.completeExceptionally(e);
            }
        }, "LazyRecipeDirectory-" + threadIndex.incrementAndGet()).start();
        return new LazyRecipeDirectory(delegate);
    }

    public RecipeRepositoryManager(Path local) {
        this.recipeLayoutManager = new RecipeLayoutManager(local);
    }

    /**
     * Returns the directories that contain the recipe information for this specific artifact
     *
     * @param  groupId    The group id
     * @param  artifactId The artifact id
     * @param  version    The version
     * @return            The path match result
     */
    @Override
    public RecipeFile lookup(Gav gav) {
        return recipeLayoutManager.lookup(gav);
    }

    @Override
    public String toString() {
        return recipeLayoutManager.toString();
    }

    public static class LazyRecipeDirectory implements RecipeDirectory {

        private final Future<RecipeDirectory> delegate;

        public LazyRecipeDirectory(Future<RecipeDirectory> delegate) {
            super();
            this.delegate = delegate;
        }

        RecipeDirectory awaitRecipeDirectory() {
            try {
                return delegate.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public RecipeFile lookup(Gav gav) {
            return awaitRecipeDirectory().lookup(gav);
        }

    }

}
