/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.domino.scm.recipes.scm;

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
import org.l2x6.jrebuild.api.scm.RemoteScmLookup;
import org.l2x6.jrebuild.api.scm.ScmRef.Kind;
import org.l2x6.jrebuild.domino.scm.recipes.BuildRecipe;
import org.l2x6.jrebuild.domino.scm.recipes.location.RecipeFile;
import org.l2x6.jrebuild.domino.scm.recipes.location.RecipeGroupManager;
import org.l2x6.pom.tuner.model.Gav;

public class GitScmLocator implements ScmLocator {

    private static final Logger log = Logger.getLogger(GitScmLocator.class);

    private static final Pattern NUMERIC_PART = Pattern.compile("(\\d+)(\\.\\d+)+");

    private final RemoteScmLookup scmLookup;

    private final RecipeGroupManager recipeGroupManager;

    public GitScmLocator(Path gitCloneBaseDir, List<String> recipeRepos, RemoteScmLookup scmLookup) {
        Objects.requireNonNull(gitCloneBaseDir, "gitCloneBaseDir");
        this.scmLookup = scmLookup;
        this.recipeGroupManager = RecipeGroupManager.of(gitCloneBaseDir, recipeRepos);
    }

    public TagInfo resolveTagInfo(Gav toBuild) {

        final RecipeFile recipe = recipeGroupManager.lookupScmInformation(toBuild);
        if (recipe == null) {
            return null;
        }
        log.tracef("Found recipe for %s: %s", toBuild, recipe);
        List<RepositoryInfo> repos = new ArrayList<>();
        List<TagMapping> allMappings = new ArrayList<>();
        ScmInfo main;
        try {
            main = BuildRecipe.SCM.getHandler().parse(recipe.recipeFile());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse " + recipe, e);
        }
        repos.add(main);
        allMappings.addAll(main.getTagMapping());
        for (var j : main.getLegacyRepos()) {
            repos.add(j);
            allMappings.addAll(j.getTagMapping());
        }

        RuntimeException firstFailure = null;
        for (RepositoryInfo parsedInfo : repos) {
            log.tracef("Looking for a tag in %s", parsedInfo.getUri());

            //now look for a tag
            try {
                final Map<String, String> tagsToHash = scmLookup.getRefs(parsedInfo.getUriWithoutFragment(), Kind.TAG);

                String version = toBuild.getVersion();
                String underscoreVersion = version.replace(".", "_");
                String selectedTag = null;

                //first try tag mappings
                for (var mapping : allMappings) {
                    log.tracef("Trying tag pattern %s on version %s", mapping.getPattern(), version);
                    Matcher m = Pattern.compile(mapping.getPattern()).matcher(version);
                    if (m.matches()) {
                        log.tracef("Tag pattern %s matches", mapping.getPattern());
                        String match = mapping.getTag();
                        for (int i = 0; i <= m.groupCount(); ++i) {
                            match = match.replaceAll("\\$" + i, m.group(i));
                        }
                        log.tracef("Trying to find tag %s", match);
                        //if the tag was a constant we don't require it to be in the tag set
                        //this allows for explicit refs to be used
                        if (tagsToHash.containsKey(match) || match.equals(mapping.getTag())) {
                            selectedTag = match;
                            break;
                        }
                    }
                }

                if (selectedTag == null) {
                    try {
                        selectedTag = runTagHeuristic(version, tagsToHash);
                    } catch (RuntimeException e) {
                        if (firstFailure == null) {
                            firstFailure = e;
                        } else {
                            firstFailure.addSuppressed(e);
                        }
                        //it is a very common pattern to use underscores instead of dots in the tags
                        selectedTag = runTagHeuristic(underscoreVersion, tagsToHash);
                    }
                }

                if (selectedTag != null) {
                    firstFailure = null;
                    String hash = tagsToHash.get(selectedTag);
                    if (hash == null) {
                        hash = selectedTag; //sometimes the tag is a hash
                    }
                    return log(new TagInfo(parsedInfo, selectedTag, hash));
                }
            } catch (RuntimeException ex) {
                log.error("Failure to determine tag", ex);
                if (firstFailure == null) {
                    firstFailure = new RuntimeException("Failed to determine tag for repo " + parsedInfo.getUri(), ex);
                } else {
                    firstFailure.addSuppressed(ex);
                }
            }
        }
        if (firstFailure != null) {
            throw firstFailure;
        }

        return null;
    }

    static TagInfo log(TagInfo result) {
        log.infof("Found tag: %s", result);
        return result;
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
                                "Could not determine tag for " + version
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
