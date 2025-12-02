/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.domino.scm;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jboss.logging.Logger;
import org.l2x6.jrebuild.api.scm.FqScmRef;
import org.l2x6.jrebuild.api.scm.RemoteScmLookup;
import org.l2x6.jrebuild.api.scm.ScmRef;
import org.l2x6.jrebuild.api.scm.ScmRef.Kind;
import org.l2x6.jrebuild.api.scm.ScmRepository;
import org.l2x6.jrebuild.common.scm.AbstractScmLocator;
import org.l2x6.jrebuild.domino.scm.recipes.BuildRecipe;
import org.l2x6.jrebuild.domino.scm.recipes.location.RecipeFile;
import org.l2x6.jrebuild.domino.scm.recipes.location.RecipeGroupManager;
import org.l2x6.jrebuild.domino.scm.recipes.scm.RepositoryInfo;
import org.l2x6.jrebuild.domino.scm.recipes.scm.ScmInfo;
import org.l2x6.jrebuild.domino.scm.recipes.scm.TagMapping;
import org.l2x6.pom.tuner.model.Gav;

public class DominoBuildRecipesScmLocator extends AbstractScmLocator {
    private static final Logger log = Logger.getLogger(DominoBuildRecipesScmLocator.class);

    private static final Pattern NUMERIC_PART = Pattern.compile("(\\d+)(\\.\\d+)+");

    private final RecipeGroupManager recipeGroupManager;

    public DominoBuildRecipesScmLocator(Path gitCloneBaseDir, List<String> recipeRepos, RemoteScmLookup scmLookup) {
        super(scmLookup);
        Objects.requireNonNull(gitCloneBaseDir, "gitCloneBaseDir");
        this.recipeGroupManager = RecipeGroupManager.of(gitCloneBaseDir, recipeRepos);
    }

    public FqScmRef locate(Gav gav) {

        final RecipeFile recipe = recipeGroupManager.lookupScmInformation(gav);
        if (recipe == null) {
            return null;
        }
        //log.tracef("Found recipe for %s: %s", toBuild, recipe);
        final List<RepositoryInfo> repos = new ArrayList<>();
        final List<TagMapping> allMappings = new ArrayList<>();
        ScmInfo main;
        try {
            main = BuildRecipe.SCM.getHandler().parse(recipe.recipeFile());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse " + recipe, e);
        }
        repos.add(main);
        allMappings.addAll(main.getTagMapping());
        for (RepositoryInfo j : main.getLegacyRepos()) {
            repos.add(j);
            allMappings.addAll(j.getTagMapping());
        }

        for (RepositoryInfo parsedInfo : repos) {
            //log.tracef("Looking for a tag in %s", parsedInfo.getUri());

            final String uri = parsedInfo.getUriWithoutFragment();
            final Map<String, String> tagsToHash = scmLookup.getRefs(uri, Kind.TAG);

            final String version = gav.getVersion();

            for (TagMapping mapping : allMappings) {
                Matcher m = Pattern.compile(mapping.getPattern()).matcher(version);
                if (m.matches()) {
                    log.tracef("%s: pattern %s matches version %s", gav, mapping.getPattern(), version);
                    String tagTemplate = mapping.getTag();
                    for (int i = 0; i <= m.groupCount(); ++i) {
                        tagTemplate = tagTemplate.replaceAll("\\$" + i, m.group(i));
                    }
                    final String sha = tagsToHash.get(tagTemplate);
                    if (sha != null) {
                        return new FqScmRef(new ScmRef(Kind.TAG, tagTemplate, sha), new ScmRepository("git", uri));
                    }
                    if (isSha1(tagTemplate)) {
                        return new FqScmRef(new ScmRef(Kind.REVISION_ID, tagTemplate, tagTemplate),
                                new ScmRepository("git", uri));
                    }
                } else {
                    log.tracef("%s: pattern %s does not match version %s", gav, mapping.getPattern(), version);
                }
                ScmRef ref = guessTag(gav, uri);
                if (ref != null) {
                    return new FqScmRef(ref, new ScmRepository("git", uri));
                }
            }
        }
        return null;
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
