/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.common.scm;

import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.regex.Pattern;
import org.eclipse.jgit.transport.URIish;
import org.l2x6.jrebuild.api.scm.RemoteScmLookup;
import org.l2x6.jrebuild.api.scm.ScmLocator;
import org.l2x6.jrebuild.api.scm.ScmRef;
import org.l2x6.jrebuild.api.scm.ScmRef.Kind;
import org.l2x6.jrebuild.api.scm.ScmRepository;
import org.l2x6.pom.tuner.model.Gav;

public abstract class AbstractScmLocator implements ScmLocator {

    private static final Pattern SHA_PATTERN = Pattern.compile("[0-9a-f]{40,}");

    static final List<BiFunction<ScmRepository, Gav, String>> VERSION_TO_TAG_FORMATTERS = List.of(
            (repo, gav) -> gav.getVersion(),
            (repo, gav) -> gav.getArtifactId() + "-" + gav.getVersion(),
            (repo, gav) -> lastPathSegment(repo).map(gitRepoName -> gitRepoName + "-" + gav.getVersion()).orElse(null),
            (repo, gav) -> "v" + gav.getVersion(),
            (repo, gav) -> "v_" + gav.getVersion(),
            (repo, gav) -> "r" + gav.getVersion(),
            // seen in commons-beanutils
            (repo, gav) -> "rel/" + gav.getVersion(),
            (repo, gav) -> "rel/" + gav.getArtifactId() + "-" + gav.getVersion(),
            (repo, gav) -> lastPathSegment(repo)
                    .map(gitRepoName -> "rel/" + gitRepoName + "-" + gav.getVersion())
                    .orElse(null),
            // Groovy
            (repo, gav) -> lastPathSegment(repo)
                    .map(gitRepoName -> gitRepoName.toUpperCase(Locale.US) + "_" + gav.getVersion().replace('.', '_'))
                    .orElse(null),
            // Javamail
            (repo, gav) -> lastPathSegment(repo)
                    .map(gitRepoName -> gitRepoName.toUpperCase(Locale.US) + "-" + gav.getVersion().replace('.', '_'))
                    .orElse(null));

    protected final RemoteScmLookup scmLookup;

    public AbstractScmLocator(RemoteScmLookup scmLookup) {
        super();
        this.scmLookup = scmLookup;
    }

    protected static ScmRef guessTag(ScmRepository repository, Gav gav, Map<String, String> tags) {
        for (BiFunction<ScmRepository, Gav, String> formatter : VERSION_TO_TAG_FORMATTERS) {
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

    protected ScmRef validateTag(ScmRepository url, String tag, String version) {
        final Kind kind = Kind.TAG;
        final String revision = scmLookup.getRevision(url, kind, tag);
        if (revision != null) {
            return new ScmRef(kind, tag, revision);
        }
        return null;
    }

    protected boolean isSha1(String revision) {
        return revision != null && SHA_PATTERN.matcher(revision).matches();
    }

    static Optional<String> lastPathSegment(ScmRepository repository) {
        if (repository.uri() == null) {
            return Optional.empty();
        }
        if ("git".equals(repository.type())) {
            try {
                URIish urish = new URIish(repository.uri());
                String p = urish.getPath();
                if (p != null) {
                    if (p.endsWith(".git")) {
                        p = p.substring(0, p.length() - 4);
                    }
                    int slashPos = p.lastIndexOf('/');
                    return Optional.of(slashPos >= 0 ? p.substring(slashPos + 1) : p);
                }
            } catch (URISyntaxException ignored) {
            }
        }
        return Optional.empty();
    }

}
