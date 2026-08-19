/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild
 *                                 project contributors as indicated by the @author tags
 *                                 SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.common.scm;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.regex.Pattern;
import org.l2x6.jrebuild.api.scm.FqScmRef.AnnotatedFqScmRef;
import org.l2x6.jrebuild.api.scm.RemoteScmLookup;
import org.l2x6.jrebuild.api.scm.Result;
import org.l2x6.jrebuild.api.scm.ScmLocator;
import org.l2x6.jrebuild.api.scm.ScmRef;
import org.l2x6.jrebuild.api.scm.ScmRef.Kind;
import org.l2x6.jrebuild.api.scm.ScmRepository.AnnotatedScmRepository;
import org.l2x6.pom.tuner.model.Gav;

public abstract class AbstractScmLocator implements ScmLocator {

    private static final Pattern SHA_PATTERN = Pattern.compile("[0-9a-f]{40,}");
    private static final String HTTPS_GITHUB_COM = "https://github.com/";
    private static final String SSH_GITHUB_COM = "ssh://git@github.com/";

    static final List<BiFunction<AnnotatedScmRepository, Gav, String>> VERSION_TO_TAG_FORMATTERS = List.of(
            (repo, gav) -> gav.getVersion(),
            (repo, gav) -> gav.getArtifactId() + "-" + gav.getVersion(),
            // seen in https://github.com/jvm-build-service-code/wsdl4j
            (repo, gav) -> gav.getArtifactId() + "-" + gav.getVersion().replace('.', '_'),
            // seen in https://github.com/jakartaee/servlet
            (repo, gav) -> gav.getVersion() + "-RELEASE",
            (repo, gav) -> repo.lastPathSegment().map(gitRepoName -> gitRepoName + "-" + gav.getVersion()).orElse(null),
            (repo, gav) -> "v" + gav.getVersion(),
            (repo, gav) -> "v_" + gav.getVersion(),
            (repo, gav) -> "r" + gav.getVersion(),
            // seen in https://github.com/eclipse-aspectj/aspectj
            (repo, gav) -> "V" + gav.getVersion().replace('.', '_'),
            // seen in commons-beanutils
            (repo, gav) -> "rel/" + gav.getVersion(),
            (repo, gav) -> "rel/" + gav.getArtifactId() + "-" + gav.getVersion(),
            (repo, gav) -> repo.lastPathSegment()
                    .map(gitRepoName -> "rel/" + gitRepoName + "-" + gav.getVersion())
                    .orElse(null),
            // Groovy
            (repo, gav) -> repo.lastPathSegment()
                    .map(gitRepoName -> gitRepoName.toUpperCase(Locale.US) + "_" + gav.getVersion().replace('.', '_'))
                    .orElse(null),
            // Javamail
            (repo, gav) -> repo.lastPathSegment()
                    .map(gitRepoName -> gitRepoName.toUpperCase(Locale.US) + "-" + gav.getVersion().replace('.', '_'))
                    .orElse(null));

    protected final RemoteScmLookup scmLookup;

    public AbstractScmLocator(RemoteScmLookup scmLookup) {
        super();
        this.scmLookup = scmLookup;
    }

    protected static ScmRef guessTag(AnnotatedScmRepository repository, Gav gav, Map<String, String> tags) {
        for (BiFunction<AnnotatedScmRepository, Gav, String> formatter : VERSION_TO_TAG_FORMATTERS) {
            final String tag = formatter.apply(repository, gav);
            if (tag != null) {
                String revision = tags.get(tag);
                if (revision != null) {
                    return new ScmRef(Kind.TAG, tag, revision);
                }
            }
        }
        return null;
    }

    protected AnnotatedFqScmRef validateTag(AnnotatedScmRepository url, String tag, String version) {
        final Kind kind = Kind.TAG;
        final Result<String, String> rev = scmLookup.getRevision(url, kind, tag);
        return rev.reduce(
                commitId -> (AnnotatedFqScmRef) new AnnotatedFqScmRef(new ScmRef(kind, tag, commitId), url),
                failure -> (AnnotatedFqScmRef) AnnotatedFqScmRef.createFailed(version, url, failure));
    }

    protected boolean isSha1(String revision) {
        return revision != null && SHA_PATTERN.matcher(revision).matches();
    }

    protected static String normalizeScmUri(String s) {
        if (s.startsWith(SSH_GITHUB_COM)) {
            s = HTTPS_GITHUB_COM + s.substring(SSH_GITHUB_COM.length());
        }

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
