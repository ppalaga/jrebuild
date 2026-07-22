/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.domino.scm;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jboss.logging.Logger;
import org.l2x6.jrebuild.api.scm.AnnotatedFqScmRef;
import org.l2x6.jrebuild.api.scm.AnnotatedScmRepository;
import org.l2x6.jrebuild.api.scm.RemoteScmLookup;
import org.l2x6.jrebuild.api.scm.Result;
import org.l2x6.jrebuild.api.scm.ScmRef;
import org.l2x6.jrebuild.api.scm.ScmRef.Kind;
import org.l2x6.jrebuild.common.scm.AbstractScmLocator;
import org.l2x6.jrebuild.domino.scm.recipes.BuildRecipe;
import org.l2x6.jrebuild.domino.scm.recipes.location.RecipeFile;
import org.l2x6.jrebuild.domino.scm.recipes.location.RecipeGroupManager;
import org.l2x6.jrebuild.domino.scm.recipes.scm.RepositoryInfo;
import org.l2x6.jrebuild.domino.scm.recipes.scm.ScmInfo;
import org.l2x6.jrebuild.domino.scm.recipes.scm.TagMapping;
import org.l2x6.pom.tuner.model.Gav;

public class DominoBuildRecipesScmLocator extends AbstractScmLocator {
    static final String SOURCE = "🁻";

    private static final Logger log = Logger.getLogger(DominoBuildRecipesScmLocator.class);

    private static final Pattern NUMERIC_PART = Pattern.compile("(\\d+)(\\.\\d+)+");

    private final RecipeGroupManager recipeGroupManager;

    public DominoBuildRecipesScmLocator(Path gitCloneBaseDir, Collection<String> recipeRepos, RemoteScmLookup scmLookup) {
        super(scmLookup);
        Objects.requireNonNull(gitCloneBaseDir, "gitCloneBaseDir");
        this.recipeGroupManager = RecipeGroupManager.of(gitCloneBaseDir, recipeRepos);
    }

    public List<AnnotatedFqScmRef> locate(Gav gav) {
        final List<RecipeFile> recipes = recipeGroupManager.lookupScmInformation(gav);
        if (recipes.isEmpty()) {
            return List.of();
        }
        //log.tracef("Found recipe for %s: %s", toBuild, recipe);
        final List<RepositoryInfo> repos = new ArrayList<>();
        final Set<TagMapping> allMappings = new LinkedHashSet<>();

        for (RecipeFile recipe : recipes) {
            ScmInfo main;
            try {
                main = BuildRecipe.SCM.getHandler().parse(recipe.recipeFile());
                log.tracef("Loaded %s info from %s", SOURCE, recipe.recipeFile());
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to parse " + recipe, e);
            }
            repos.add(main);
            allMappings.addAll(main.getTagMapping());
            for (RepositoryInfo j : main.getLegacyRepos()) {
                repos.add(j);
                allMappings.addAll(j.getTagMapping());
            }
        }

        final List<AnnotatedFqScmRef> result = new ArrayList<>();
        for (RepositoryInfo repositoryInfo : repos) {
            final String type = repositoryInfo.getType();
            final AnnotatedScmRepository uri = new AnnotatedScmRepository(SOURCE, type == null ? "git" : type,
                    repositoryInfo.getUriWithoutFragment());
            log.debugf("Mapping %s to a tag in %s with mappings %s", gav, uri, allMappings);

            try {
                final Result<Map<String, String>, String> tagsToHashResult = scmLookup.getRefs(uri, Kind.TAG);
                final String version = gav.getVersion();
                if (tagsToHashResult.isFailure()) {
                    result.add(AnnotatedFqScmRef.createFailed(version, uri, tagsToHashResult.failure()));
                } else {
                    Map<String, String> tagsToHash = tagsToHashResult.result();
                    for (TagMapping mapping : allMappings) {
                        Matcher m = mapping.pattern().matcher(version);
                        if (m.matches()) {
                            log.tracef("%s: pattern %s matches version %s", gav, mapping.getPattern(), version);
                            String tagTemplate = mapping.getTag();
                            for (int i = 0; i <= m.groupCount(); ++i) {
                                tagTemplate = tagTemplate.replaceAll("\\$" + i, m.group(i));
                            }
                            final String sha = tagsToHash.get(tagTemplate);
                            if (sha != null) {
                                return List.of(new AnnotatedFqScmRef(new ScmRef(Kind.TAG, tagTemplate, sha), uri));
                            }
                            if (isSha1(tagTemplate)) {
                                return List.of(new AnnotatedFqScmRef(new ScmRef(Kind.COMMIT, tagTemplate, tagTemplate),
                                        uri));
                            }
                        } else {
                            log.tracef("%s: pattern %s does not match version %s", gav, mapping.getPattern(), version);
                        }
                    }
                    ScmRef ref = guessTag(uri, gav, tagsToHash);
                    if (ref != null) {
                        return List.of(new AnnotatedFqScmRef(ref, uri));
                    }
                    result.add(AnnotatedFqScmRef.createFailed(version, uri,
                            "Tag not found for version " + version + " in " + uri));
                }
            } catch (Exception e) {
                final StringWriter sw = new StringWriter();
                final String msg = "Could not find SCM ref for " + gav + " in " + uri;
                sw.append(msg).append("\n");
                try (PrintWriter pw = new PrintWriter(sw)) {
                    e.printStackTrace(pw);
                }
                result.add(AnnotatedFqScmRef.createFailed(gav.getVersion(), uri, sw.toString()));
            }
        }
        return Collections.unmodifiableList(result);
    }

    static String runTagHeuristic(String version, Map<String, String> tagsToHash) {
        String selectedTag = null;
        Set<String> versionExactContains = new HashSet<>();
        Set<String> tagExactContains = new HashSet<>();
        for (var name : tagsToHash.keySet()) {
            if (name.equals(version)) {
                //exact match is always good
                selectedTag = version;
                break;
            } else if (name.contains(version)) {
                versionExactContains.add(name);
            } else if (version.contains(name)) {
                tagExactContains.add(name);
            }
        }
        if (selectedTag != null) {
            return selectedTag;
        }

        //no exact match
        if (versionExactContains.size() == 1) {
            //only one contained the full version
            selectedTag = versionExactContains.iterator().next();
        } else {
            for (var i : versionExactContains) {
                //look for a tag that ends with the version (i.e. no -rc1 or similar)
                if (i.endsWith(version)) {
                    if (selectedTag == null) {
                        selectedTag = i;
                    } else {
                        throw new RuntimeException(
                                "c " + version
                                        + " multiple possible tags were found: "
                                        + versionExactContains);
                    }
                }
            }
            if (selectedTag == null && tagExactContains.size() == 1) {
                //this is for cases where the tag is something like 1.2.3 and the version is 1.2.3.Final
                //we need to be careful though, as e.g. this could also make '1.2' match '1.2.3'
                //we make sure the numeric part is an exact match
                var tempTag = tagExactContains.iterator().next();
                Matcher tm = NUMERIC_PART.matcher(tempTag);
                Matcher vm = NUMERIC_PART.matcher(version);
                if (tm.find() && vm.find()) {
                    if (Objects.equals(tm.group(0), vm.group(0))) {
                        selectedTag = tempTag;
                    }
                }
            }
            if (selectedTag == null) {
                RuntimeException runtimeException = new RuntimeException(
                        "Could not determine tag for " + version);
                runtimeException.setStackTrace(new StackTraceElement[0]);
                throw runtimeException;
            }
        }
        return selectedTag;
    }

}
