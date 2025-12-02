/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.reproducible.central;

import java.nio.file.Path;
import org.jboss.logging.Logger;
import org.l2x6.jrebuild.api.scm.FqScmRef;
import org.l2x6.jrebuild.api.scm.RemoteScmLookup;
import org.l2x6.jrebuild.api.scm.ScmRef;
import org.l2x6.jrebuild.api.scm.ScmRepository;
import org.l2x6.jrebuild.common.git.GitUtils;
import org.l2x6.jrebuild.common.scm.AbstractScmLocator;
import org.l2x6.jrebuild.reproducible.central.api.Buildspec;
import org.l2x6.jrebuild.reproducible.central.api.BuildspecRepository;
import org.l2x6.pom.tuner.model.Gav;

public class ReproducibleCentralScmLocator extends AbstractScmLocator {
    private static final Logger log = Logger.getLogger(ReproducibleCentralScmLocator.class);
    private final BuildspecRepository buildspecRepository;

    public ReproducibleCentralScmLocator(Path gitCloneBaseDir, String reproducibleCentralGitRepository,
            RemoteScmLookup scmLookup) {
        super(scmLookup);
        final Path workingCopyDir = gitCloneBaseDir.resolve(GitUtils.uriToFileName(reproducibleCentralGitRepository));
        this.buildspecRepository = ReproducibleCentralLayout.cloneOrFetch(reproducibleCentralGitRepository, "master",
                workingCopyDir);
    }

    public FqScmRef locate(Gav gav) {
        Buildspec recipe = buildspecRepository.lookup(gav);
        if (recipe != null) {
            String url = recipe.gitRepo();
            ScmRef ref = validateTag(url, recipe.gitTag(), gav.getVersion());
            return new FqScmRef(ref, new ScmRepository("git", url));
        }
        return null;
    }

}
