/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.scm;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.apache.maven.model.Model;
import org.apache.maven.model.Scm;
import org.l2x6.jrebuild.api.scm.FqScmRef;
import org.l2x6.jrebuild.api.scm.RemoteScmLookup;
import org.l2x6.jrebuild.api.scm.ScmLocator;
import org.l2x6.jrebuild.api.scm.ScmRef;
import org.l2x6.jrebuild.api.scm.ScmRef.Kind;
import org.l2x6.jrebuild.api.scm.ScmRepository;
import org.l2x6.pom.tuner.model.Gav;

public class PomScmLocator implements ScmLocator {
    private final Function<Gav, Model> getEffectiveModel;
    private final RemoteScmLookup scmLookup;

    public PomScmLocator(Function<Gav, Model> getEffectiveModel, RemoteScmLookup scmLookup) {
        super();
        this.getEffectiveModel = getEffectiveModel;
        this.scmLookup = scmLookup;
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
            return new FqScmRef(guessTag(gav, repository.uri()), repository);
        }
        return new FqScmRef(validateTag(repository.uri(), tag, gav.getVersion()), repository);
    }

    private ScmRef guessTag(Gav gav, String url) {
        final Map<String, String> tags = scmLookup.getRefs(url, Kind.TAG);
        final String version = gav.getVersion();
        String tag = version;
        String revision = tags.get(tag);
        if (revision != null) {
            return new ScmRef(Kind.TAG, tag, revision);
        }
        tag = gav.getArtifactId() + "-" + version;
        revision = tags.get(tag);
        if (revision != null) {
            return new ScmRef(Kind.TAG, tag, revision);
        }
        // TODO: we could check the parent artifactIds while groupId stays the same

        tag = "v" + version;
        revision = tags.get(tag);
        if (revision != null) {
            return new ScmRef(Kind.TAG, tag, revision);
        }
        return ScmRef.createUnknown(version);
    }

    private ScmRef validateTag(String url, String tag, String version) {
        final Kind kind = Kind.TAG;
        final String revision = scmLookup.getRevision(url, kind, tag);
        if (revision != null) {
            return new ScmRef(kind, tag, revision);
        }
        return ScmRef.createUnknown(version);
    }

    private static final String HTTPS_GITHUB_COM = "https://github.com/";

    private static String normalizeScmUri(String s) {
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
