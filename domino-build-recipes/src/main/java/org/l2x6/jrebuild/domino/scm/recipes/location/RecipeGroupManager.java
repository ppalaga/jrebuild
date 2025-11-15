/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.domino.scm.recipes.location;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.jboss.logging.Logger;
import org.l2x6.pom.tuner.model.Gav;

/**
 * Entry point for requesting build information
 */
public class RecipeGroupManager {

    private static final Logger log = Logger.getLogger(RecipeGroupManager.class.getName());
    private static final Pattern remotePattern = Pattern.compile("(?!file\\b)\\w+?:\\/\\/.*");

    /**
     * The repositories, the highest priority first
     */
    private final List<RecipeDirectory> repositories;

    public static RecipeGroupManager of(Path gitCloneBaseDir, List<String> recipeRepos) {
        //checkout the git recipe database and load the recipes
        final List<RecipeDirectory> managers = new ArrayList<>(recipeRepos.size());
        for (var url : recipeRepos) {
            final RecipeDirectory repoManager;
            if (isCloneable(url)) {
                try {
                    final Path workingCopyDir = gitCloneBaseDir.resolve(uriToFileName(url));
                    Files.createDirectories(workingCopyDir);
                    repoManager = RecipeRepositoryManager.create(url, workingCopyDir);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to checkout " + url, e);
                }
            } else {
                final Path p;
                if (url.startsWith("file://")) {
                    p = Path.of(url.substring("file://".length()));
                } else {
                    p = Path.of(url);
                }
                repoManager = new RecipeLayoutManager(p);
            }
            managers.add(repoManager);
        }
        return new RecipeGroupManager(managers);
    }

    private static boolean isCloneable(String url) {
        return remotePattern.matcher(url).matches()
                || (url.startsWith("file://") && (url.endsWith("/.git") || url.endsWith("\\.git")));
    }

    public RecipeGroupManager(List<RecipeDirectory> repositories) {
        this.repositories = repositories;
    }

    public RecipeFile lookupScmInformation(Gav gav) {
        return repositories.stream()
                .map(repo -> repo.lookup(gav))
                .filter(r -> r != null)
                .sorted()
                .findFirst()
                .orElse(null);
    }

    public static String uriToFileName(String uri) {
        return uri.replaceAll("^(http:|https:|git(\\+ssh)?:|ssh:|file:)/+", "")
                .replaceAll("^git@", "")
                .replaceAll("[^A-Za-z0-9._-]+", "-")
                .replace("-[\\-]+", "-")
                .replaceAll("^[-.]+", "")
                .replaceAll("[-.]+$", "")
                .replaceAll("\\.git$", "")
                .replaceAll("[-.]+$", "");
    }

    public static String normalizeScmUri(String scmUri) {
        // Remove any fragment
        int pos = scmUri.indexOf("#");
        if (pos != -1) {
            scmUri = scmUri.substring(0, pos);
        }
        if (scmUri.endsWith(".git")) {
            scmUri = scmUri.substring(0, scmUri.length() - 4);
        }
        pos = scmUri.indexOf("://");
        if (pos != -1) {
            scmUri = scmUri.substring(pos + 3);
        }
        return scmUri;
    }

}
