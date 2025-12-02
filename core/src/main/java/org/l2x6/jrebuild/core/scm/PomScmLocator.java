/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.scm;

import java.util.Objects;
import java.util.function.Function;
import org.apache.maven.model.Model;
import org.apache.maven.model.Scm;
import org.l2x6.jrebuild.api.scm.FqScmRef;
import org.l2x6.jrebuild.api.scm.RemoteScmLookup;
import org.l2x6.jrebuild.api.scm.ScmRef;
import org.l2x6.jrebuild.api.scm.ScmRepository;
import org.l2x6.jrebuild.common.scm.AbstractScmLocator;
import org.l2x6.pom.tuner.model.Gav;

public class PomScmLocator extends AbstractScmLocator {
    private static final String HTTPS_GITHUB_COM = "https://github.com/";
    private final Function<Gav, Model> getEffectiveModel;

    public PomScmLocator(Function<Gav, Model> getEffectiveModel, RemoteScmLookup scmLookup) {
        super(scmLookup);
        this.getEffectiveModel = getEffectiveModel;
    }

    @Override
    public FqScmRef locate(Gav gav) {
        final Model effectiveModel = getEffectiveModel.apply(gav);
        final Scm scm = effectiveModel.getScm();
        if (scm != null) {
            if (scm.getConnection() != null && !scm.getConnection().isEmpty()) {
                return toScmRef(gav, scm, scm.getConnection());
            }
            String url = scm.getUrl();
            if (url != null && url.startsWith("https://github.com/")) {
                return toScmRef(gav, scm, url);
            }
        }
        String url = effectiveModel.getUrl();
        if (url != null && url.startsWith("https://github.com/")) {
            return toScmRef(gav, scm, url);
        }
        return null;
    }

    private FqScmRef toScmRef(Gav gav, final Scm scm, String url) {
        return of(gav, scm.getTag(), new ScmRepository("git", normalizeScmUri(url)));
    }

    public FqScmRef of(Gav gav, String tag, ScmRepository repository) {
        Objects.requireNonNull(repository, "repository cannot be null");
        if (tag == null || "HEAD".equals(tag)) {
            final ScmRef ref = guessTag(gav, repository.uri());
            return new FqScmRef(ref != null ? ref : ScmRef.createUnknown(gav.getVersion()), repository);
        }
        try {
            final ScmRef ref = validateTag(repository.uri(), tag, gav.getVersion());
            return new FqScmRef(ref, repository);
        } catch (Exception e) {
            throw new RuntimeException("Invalid SCM info in pom of " + gav, e);
        }
    }

    protected static String normalizeScmUri(String s) {
        s = s.replace("scm:", "");
        s = s.replace("git:", "");
        s = s.replace("git@", "");
        s = s.replace("ssh:", "");
        s = s.replace("svn:", "");
        // s = s.replace(".git", "");
        if (s.startsWith("http://")) {
            s = s.replace("http://", "https://");
        } else if (!s.startsWith("https://")) {
            s = s.replace(':', '/');
            if (s.startsWith("github.com:")) {
                s = s.replace(':', '/');
            }
            if (s.startsWith("//")) {
                s = "https:" + s;
            } else {
                s = "https://" + s;
            }
        }
        if (s.startsWith(HTTPS_GITHUB_COM)) {
            var tmp = s.substring(HTTPS_GITHUB_COM.length());
            final String[] parts = tmp.split("/");
            if (parts.length > 2) {
                s = HTTPS_GITHUB_COM + parts[0] + "/" + parts[1];
            }
        }
        return s;
    }

}
