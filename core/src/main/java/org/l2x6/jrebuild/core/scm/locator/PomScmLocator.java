/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.scm.locator;

import java.util.function.Function;
import org.apache.maven.model.Model;
import org.apache.maven.model.Scm;
import org.l2x6.jrebuild.core.scm.ScmLocator;
import org.l2x6.jrebuild.core.scm.ScmRef;
import org.l2x6.jrebuild.core.scm.ScmRepository;
import org.l2x6.pom.tuner.model.Gav;

public class PomScmLocator implements ScmLocator {
    private final Function<Gav, Model> getEffectiveModel;

    public PomScmLocator(Function<Gav, Model> getEffectiveModel) {
        super();
        this.getEffectiveModel = getEffectiveModel;
    }

    @Override
    public ScmRef locate(Gav gav) {
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

    private static ScmRef toScmRef(Gav gav, final Scm scm, String url) {
        return ScmRef.of(scm.getTag(), new ScmRepository("git", normalizeScmUri(url)));
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
