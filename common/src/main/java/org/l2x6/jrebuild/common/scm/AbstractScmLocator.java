/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.common.scm;

import java.util.Map;
import java.util.regex.Pattern;
import org.l2x6.jrebuild.api.scm.RemoteScmLookup;
import org.l2x6.jrebuild.api.scm.ScmLocator;
import org.l2x6.jrebuild.api.scm.ScmRef;
import org.l2x6.jrebuild.api.scm.ScmRef.Kind;
import org.l2x6.pom.tuner.model.Gav;

public abstract class AbstractScmLocator implements ScmLocator {

    private static final Pattern SHA_PATTERN = Pattern.compile("[0-9a-f]{40,}");

    protected final RemoteScmLookup scmLookup;

    public AbstractScmLocator(RemoteScmLookup scmLookup) {
        super();
        this.scmLookup = scmLookup;
    }

    protected ScmRef guessTag(Gav gav, String url) {
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
        tag = "v_" + version;
        revision = tags.get(tag);
        if (revision != null) {
            return new ScmRef(Kind.TAG, tag, revision);
        }
        tag = "r" + version;
        revision = tags.get(tag);
        if (revision != null) {
            return new ScmRef(Kind.TAG, tag, revision);
        }
        return null;
    }

    protected ScmRef validateTag(String url, String tag, String version) {
        final Kind kind = Kind.TAG;
        final String revision = scmLookup.getRevision(url, kind, tag);
        if (revision != null) {
            return new ScmRef(kind, tag, revision);
        }
        return ScmRef.createUnknown(version);
    }

    protected boolean isSha1(String revision) {
        return revision != null && SHA_PATTERN.matcher(revision).matches();
    }
}
