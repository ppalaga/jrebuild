/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.domino.scm.recipes.location;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jboss.logging.Logger;
import org.l2x6.jrebuild.domino.scm.recipes.location.RecipeFile.Priority;
import org.l2x6.jrebuild.domino.scm.recipes.util.ComparableVersion;
import org.l2x6.pom.tuner.model.Gav;

/**
 * Manages an individual recipe database of build recipes.
 * <p>
 * Layout is specified as:
 * <p>
 * /recipes/io/quarkus/ //location information for everything in the io/quarkus group
 * /recipes/io/quarkus/security/ //info for the io.quarkus.security group
 * /recipes/io/quarkus/_artifact/quarkus-core/ //artifact level information for quarkus-core (hopefully not common)
 * /recipes/io/quarkus/_version/2.2.0-rhosk3/ //location information for version 2.2.0-rhosk3
 * /recipes/io/quarkus/_artifact/quarkus-core/_version/2.2.0-rhosk3/ //artifact level information for a specific version
 * of
 * quarkus core
 * <p>
 * Different pieces of information are stored in different files in these directories specified above, and it is
 * possible
 * to only override some parts of the recipe (e.g. a different location for a service specific version, but everything
 * else is
 * the same)
 * <p>
 * At present this is just the location information.
 */
public class RecipeLayoutManager implements RecipeDirectory {

    private static final Logger log = Logger.getLogger(RecipeLayoutManager.class);

    public static final String ARTIFACT = "_artifact";
    public static final String VERSION = "_version";
    private final Path baseDirectory;
    private final Path scmInfoDirectory;
    //    private final Path buildInfoDirectory;
    //    private final Path repositoryInfoDirectory;
    //    private final Path buildToolInfoDirectory;
    //    private final Path pluginInfoDirectory;

    public RecipeLayoutManager(Path baseDirectory) {
        this.baseDirectory = baseDirectory;
        scmInfoDirectory = baseDirectory.resolve(RecipeRepositoryManager.SCM_INFO);
        //        buildInfoDirectory = baseDirectory.resolve(RecipeRepositoryManager.BUILD_INFO);
        //        repositoryInfoDirectory = baseDirectory.resolve(RecipeRepositoryManager.REPOSITORY_INFO);
        //        buildToolInfoDirectory = baseDirectory.resolve(RecipeRepositoryManager.BUILD_TOOL_INFO);
        //        pluginInfoDirectory = baseDirectory.resolve(RecipeRepositoryManager.DISABLED_PLUGINS);
    }

    //
    // @Override
    // public <T> void writeBuildData(AddBuildRecipeRequest<T> data) {
    // Path target = buildInfoDirectory.resolve(RecipeGroupManager.normalizeScmUri(data.getScmUri()));
    // if (data.getVersion() != null) {
    // target = target.resolve(VERSION).resolve(data.getVersion());
    // }
    // try {
    // Files.createDirectories(target);
    // data.getRecipe().getHandler().write(data.getData(), target.resolve(data.getRecipe().getName()));
    // } catch (IOException e) {
    // throw new RuntimeException(e);
    // }
    // }
    //
    // @Override
    // public <T> void writeArtifactData(AddRecipeRequest<T> data) {
    // String groupId = data.getGroupId();
    // String artifactId = data.getArtifactId();
    // String version = data.getVersion();
    // Path resolved = scmInfoDirectory.resolve(groupId.replace('.', File.separatorChar));
    // if (artifactId != null) {
    // resolved = resolved.resolve(ARTIFACT);
    // resolved = resolved.resolve(artifactId);
    // }
    // if (version != null) {
    // resolved = resolved.resolve(VERSION);
    // resolved = resolved.resolve(version);
    // }
    // try {
    // Files.createDirectories(resolved);
    // data.getRecipe().getHandler().write(data.getData(), resolved.resolve(data.getRecipe().getName()));
    // } catch (IOException e) {
    // throw new RuntimeException(e);
    // }
    // }

    @Override
    public RecipeFile lookup(Gav gav) {

        final Path groupFolder = scmInfoDirectory.resolve(gav.getGroupId().replace('.', File.separatorChar));
        if (log.isDebugEnabled()) {
            log.debugf("Searching for recipe in %s", shortenPath(groupFolder));
        }
        if (Files.notExists(groupFolder)) {
            return null;
        }
        final Path artifactFolder = groupFolder.resolve(ARTIFACT);
        final Path artifactPath = artifactFolder.resolve(gav.getArtifactId());
        final ComparableVersion requestedVersion = new ComparableVersion(gav.getVersion());
        if (Files.exists(artifactPath)) {
            final Path gavRecipeFile = resolveVersion(artifactPath, requestedVersion, "scm.yaml");
            if (gavRecipeFile != null) {
                return new RecipeFile(Priority.GAV, gavRecipeFile);
            }
            final Path gaRecipeFile = artifactPath.resolve("scm.yaml");
            if (Files.isRegularFile(gaRecipeFile)) {
                return new RecipeFile(Priority.GA, gaRecipeFile);
            }
        }
        final Path gvRecipeFile = resolveVersion(groupFolder, requestedVersion, "scm.yaml");
        if (gvRecipeFile != null) {
            return new RecipeFile(Priority.GV, gvRecipeFile);
        }
        final Path gRecipeFile = groupFolder.resolve("scm.yaml");
        if (Files.isRegularFile(gRecipeFile)) {
            return new RecipeFile(Priority.G, gRecipeFile);
        }
        return null;
    }

    Path shortenPath(Path p) {
        return baseDirectory.getParent().relativize(p);
    }
    //
    //    @Override
    //    public Optional<Path> getBuildPaths(String scmUri, String version) {
    //        Path target = buildInfoDirectory.resolve(RecipeGroupManager.normalizeScmUri(scmUri));
    //        if (!Files.exists(target)) {
    //            return Optional.empty();
    //        }
    //        Path v = resolveVersion(target, version);
    //        return Optional.ofNullable(v != null ? v : target);
    //    }
    //
    //    @Override
    //    public Optional<Path> getRepositoryPaths(String name) {
    //        Path target = repositoryInfoDirectory.resolve(name + ".yaml");
    //        if (Files.exists(target)) {
    //            return Optional.of(target);
    //        }
    //        return Optional.empty();
    //    }
    //
    //    @Override
    //    public Optional<Path> getBuildToolInfo(String name) {
    //        Path target = buildToolInfoDirectory.resolve(name).resolve("tool.yaml");
    //        if (Files.exists(target)) {
    //            return Optional.of(target);
    //        }
    //        return Optional.empty();
    //    }
    //
    //    @Override
    //    public List<Path> getAllRepositoryPaths() {
    //        if (Files.notExists(repositoryInfoDirectory)) {
    //            return List.of();
    //        }
    //        try (Stream<Path> list = Files.list(repositoryInfoDirectory)) {
    //            return list.filter(s -> s.toString().endsWith(".yaml")).collect(Collectors.toList());
    //        } catch (IOException e) {
    //            throw new RuntimeException(e);
    //        }
    //    }
    //
    //    @Override
    //    public Optional<Path> getDisabledPlugins(String tool) {
    //        Path target = pluginInfoDirectory.resolve(tool + ".yaml");
    //        return Files.isReadable(target) ? Optional.of(target) : Optional.empty();
    //    }

    /**
     * @param  target
     * @param  version
     * @return         an existing {@link Path} or {@code null} if no suitable path exists
     */
    private Path resolveVersion(Path target, ComparableVersion requestedVersion, String recipeFileName) {
        final Path versions = target.resolve(VERSION);
        if (!Files.exists(versions)) {
            return null;
        }
        ComparableVersion currentVersion = null;
        Path currentPath = null;
        try (var s = Files.list(versions)) {
            var i = s.iterator();
            while (i.hasNext()) {
                Path path = i.next();
                ComparableVersion pv = new ComparableVersion(path.getFileName().toString());
                if (requestedVersion.compareTo(pv) <= 0) {
                    if (currentVersion == null || pv.compareTo(currentVersion) < 0) {
                        currentVersion = pv;
                        currentPath = path;
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (currentPath == null) {
            return null;
        }
        final Path recipeFile = currentPath.resolve(recipeFileName);
        if (!Files.isRegularFile(recipeFile)) {
            throw new IllegalStateException(recipeFile + " must exist when " + currentPath + " exists");
        }
        return recipeFile;
    }
    //
    //    @Override
    //    public <T> void writeBuildData(AddBuildRecipeRequest<T> data) {
    //        Path target = buildInfoDirectory.resolve(RecipeGroupManager.normalizeScmUri(data.getScmUri()));
    //        if (data.getVersion() != null) {
    //            target = target.resolve(VERSION).resolve(data.getVersion());
    //        }
    //        try {
    //            Files.createDirectories(target);
    //            data.getRecipe().getHandler().write(data.getData(), target.resolve(data.getRecipe().getName()));
    //        } catch (IOException e) {
    //            throw new RuntimeException(e);
    //        }
    //    }
    //
    //    @Override
    //    public <T> void writeArtifactData(AddRecipeRequest<T> data) {
    //        String groupId = data.getGroupId();
    //        String artifactId = data.getArtifactId();
    //        String version = data.getVersion();
    //        Path resolved = scmInfoDirectory.resolve(groupId.replace('.', File.separatorChar));
    //        if (artifactId != null) {
    //            resolved = resolved.resolve(ARTIFACT);
    //            resolved = resolved.resolve(artifactId);
    //        }
    //        if (version != null) {
    //            resolved = resolved.resolve(VERSION);
    //            resolved = resolved.resolve(version);
    //        }
    //        try {
    //            Files.createDirectories(resolved);
    //            data.getRecipe().getHandler().write(data.getData(), resolved.resolve(data.getRecipe().getName()));
    //        } catch (IOException e) {
    //            throw new RuntimeException(e);
    //        }
    //    }
}
