/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.domino.scm.recipes.location;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.jboss.logging.Logger;
import org.l2x6.jrebuild.domino.scm.recipes.BuildRecipe;
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

    public List<Path> lookupScmInformation(Gav gav) {

        List<Path> artifactVersionResults = new ArrayList<>();
        List<Path> artifactResults = new ArrayList<>();
        List<Path> versionResults = new ArrayList<>();
        List<Path> groupResults = new ArrayList<>();

        var group = gav.getGroupId();
        log.tracef("Looking up %s", group);

        List<RecipePathMatch> paths = new ArrayList<>();
        //we need to do a lookup
        for (RecipeDirectory r : repositories) {
            Optional<RecipePathMatch> possible = r.getArtifactPaths(gav.getGroupId(), gav.getArtifactId(),
                    gav.getVersion());
            if (possible.isPresent()) {
                paths.add(possible.get());
            }
        }

        for (RecipePathMatch path : paths) {
            if (path.getArtifactAndVersion() != null) {
                //if there is a file specific to this group, artifact and version it takes priority
                Path resolvedPath = path.getArtifactAndVersion().resolve(BuildRecipe.SCM.getName());
                log.tracef("Searching for recipe in %s for specific path for GAV", resolvedPath);
                if (Files.exists(resolvedPath)) {
                    artifactVersionResults.add(resolvedPath);
                }
            }
            if (path.getArtifact() != null) {
                Path resolvedPath = path.getArtifact().resolve(BuildRecipe.SCM.getName());
                log.tracef("Searching for recipe in %s for specific path for GAV", resolvedPath);
                if (Files.exists(resolvedPath)) {
                    artifactResults.add(resolvedPath);
                }
            }
            if (path.getVersion() != null) {
                Path resolvedPath = path.getVersion().resolve(BuildRecipe.SCM.getName());
                log.tracef("Searching for recipe in %s for specific path for GAV", resolvedPath);
                if (Files.exists(resolvedPath)) {
                    versionResults.add(resolvedPath);
                }
            }
            if (path.getGroup() != null) {
                Path resolvedPath = path.getGroup().resolve(BuildRecipe.SCM.getName());
                log.tracef("Searching for recipe in %s for specific path for GAV", resolvedPath);
                if (Files.exists(resolvedPath)) {
                    groupResults.add(resolvedPath);
                }
            }
        }
        if (!artifactVersionResults.isEmpty()) {
            return artifactVersionResults;
        }
        if (!artifactResults.isEmpty()) {
            return artifactResults;
        }
        if (!versionResults.isEmpty()) {
            return versionResults;
        }
        return groupResults;
    }

    public BuildInfoResponse requestBuildInformation(BuildInfoRequest buildInfoRequest) {

        String scmUri = normalizeScmUri(buildInfoRequest.getScmUri());

        List<Path> paths = new ArrayList<>();
        for (var r : repositories) {
            var possible = r.getBuildPaths(scmUri, buildInfoRequest.getVersion());
            if (possible.isPresent()) {
                paths.add(possible.get());
            }
        }

        Map<BuildRecipe, Path> buildResults = new HashMap<>();
        for (var recipe : buildInfoRequest.getRecipeFiles()) {
            for (var path : paths) {
                var option = path.resolve(recipe.getName());
                if (Files.exists(option)) {
                    buildResults.put(recipe, option);
                    break;
                }
            }

        }
        return new BuildInfoResponse(buildResults);
    }

    public static String uriToFileName(String uri) {
        return uri.replaceAll("^(http:|https:|git(\\+ssh)?:|ssh:|file:)/+", "")
                .replaceAll("^git@", "")
                .replaceAll("[^A-Za-z0-9._-]+", "-")
                .replace("-[\\-]+", "-")
                .replaceAll("^[-.]+", "")
                .replaceAll("[-.]+$", "")
                .replaceAll("\\.git$", "");
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
