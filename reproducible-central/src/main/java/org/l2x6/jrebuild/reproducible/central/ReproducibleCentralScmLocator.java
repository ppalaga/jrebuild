/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.reproducible.central;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.jboss.logging.Logger;
import org.l2x6.jrebuild.api.scm.FqScmRef.AnnotatedFqScmRef;
import org.l2x6.jrebuild.api.scm.RemoteScmLookup;
import org.l2x6.jrebuild.api.scm.ScmRepository.AnnotatedScmRepository;
import org.l2x6.jrebuild.common.git.GitUtils;
import org.l2x6.jrebuild.common.scm.AbstractScmLocator;
import org.l2x6.jrebuild.reproducible.central.api.Buildspec;
import org.l2x6.jrebuild.reproducible.central.api.BuildspecRepository;
import org.l2x6.pom.tuner.model.Gav;

public class ReproducibleCentralScmLocator extends AbstractScmLocator {
    private static final Logger log = Logger.getLogger(ReproducibleCentralScmLocator.class);
    private static final String SOURCE = "🌏︎";
    private final List<BuildspecRepository> buildspecRepositories;

    public ReproducibleCentralScmLocator(
            Path gitCloneBaseDir,
            Path cacheDir,
            Collection<String> reproducibleCentralGitRepositories,
            RemoteScmLookup scmLookup) {
        super(scmLookup);
        final List<BuildspecRepository> result = new ArrayList<>();
        for (String url : reproducibleCentralGitRepositories) {
            final Path workingCopyDir = gitCloneBaseDir.resolve(GitUtils.uriToFileName(url));
            result.add(ReproducibleCentralLayout.cloneOrFetch(url, "master", workingCopyDir,
                    cacheDir.resolve("reproducible-central-index"), cacheDir));
        }
        this.buildspecRepositories = Collections.unmodifiableList(result);
    }

    public List<AnnotatedFqScmRef> locate(Gav gav) {
        List<AnnotatedFqScmRef> result = new ArrayList<>();
        for (BuildspecRepository repo : buildspecRepositories) {
            Buildspec recipe = repo.lookup(gav);
            if (recipe != null) {
                AnnotatedScmRepository uri = new AnnotatedScmRepository(SOURCE, "git", recipe.gitRepo());
                try {
                    String tag = recipe.gitTag();
                    AnnotatedFqScmRef ref = validateTag(uri, tag, gav.getVersion());
                    if (ref.isFailed()) {
                        result.add(ref);
                    } else {
                        return List.of(ref);
                    }
                    //                    final String msg = "Could not find SCM revision for tag " + tag + " declared in " + recipe.file() + " for "
                    //                            + gav + " in " + uri;
                    //                    result.add(createFailed(gav, uri, msg));
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
        }
        return Collections.unmodifiableList(result);
    }

}
