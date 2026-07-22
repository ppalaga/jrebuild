/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.scm;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.maven.model.Model;
import org.apache.maven.model.Scm;
import org.jboss.logging.Logger;
import org.l2x6.jrebuild.api.scm.AnnotatedFqScmRef;
import org.l2x6.jrebuild.api.scm.AnnotatedScmRepository;
import org.l2x6.jrebuild.api.scm.RemoteScmLookup;
import org.l2x6.jrebuild.api.scm.Result;
import org.l2x6.jrebuild.api.scm.ScmRef;
import org.l2x6.jrebuild.api.scm.ScmRef.Kind;
import org.l2x6.jrebuild.common.scm.AbstractScmLocator;
import org.l2x6.pom.tuner.model.Gav;

public class PomScmLocator extends AbstractScmLocator {
    private static final Logger log = Logger.getLogger(PomScmLocator.class);
    private static final String SOURCE = "♢";
    private static final Pattern SCM_TYPE_PATTERN = Pattern.compile("^scm\\:([^\\|\\:]+)[\\|\\:](.*)$");
    private final Function<Gav, Model> getEffectiveModel;

    public PomScmLocator(Function<Gav, Model> getEffectiveModel, RemoteScmLookup scmLookup) {
        super(scmLookup);
        this.getEffectiveModel = getEffectiveModel;
    }

    @Override
    public List<AnnotatedFqScmRef> locate(Gav gav) {
        final Model effectiveModel = getEffectiveModel.apply(gav);
        final Scm scm = effectiveModel.getScm();
        final List<AnnotatedFqScmRef> result = new ArrayList<>();
        final Set<AnnotatedScmRepository> visitedRepos = new HashSet<>();
        if (scm != null) {
            List<Supplier<String>> uris = List.of(scm::getConnection, scm::getDeveloperConnection, () -> {
                String url = scm.getUrl();
                return url != null && url.startsWith("https://github.com/") ? url : null;
            });
            for (Supplier<String> supplier : uris) {
                String url = supplier.get();
                if (url != null) {
                    AnnotatedScmRepository repo = toScmRepository(url);
                    if (visitedRepos.add(repo)) {
                        AnnotatedFqScmRef ref = of(gav, scm.getTag(), repo);
                        if (!ref.isUnknownOrFailed()) {
                            return List.of(ref);
                        }
                        result.add(ref);
                    }
                }
            }
        }
        String url = effectiveModel.getUrl();
        if (url != null && url.startsWith("https://github.com/")) {
            AnnotatedScmRepository repo = toScmRepository(url);
            if (visitedRepos.add(repo)) {
                AnnotatedFqScmRef ref = of(gav, scm.getTag(), repo);
                if (!ref.isUnknownOrFailed()) {
                    return List.of(ref);
                }
                result.add(ref);
            }
        }
        return Collections.unmodifiableList(result);
    }

    static AnnotatedScmRepository toScmRepository(String url) {
        Matcher m = SCM_TYPE_PATTERN.matcher(url);
        if (m.matches()) {
            return new AnnotatedScmRepository(SOURCE, m.group(1), normalizeScmUri(m.group(2)));
        }
        return new AnnotatedScmRepository(SOURCE, "git", normalizeScmUri(url));
    }

    public AnnotatedFqScmRef of(Gav gav, String tag, AnnotatedScmRepository uri) {
        Objects.requireNonNull(uri, "repository cannot be null");
        try {
            if (tag == null || "HEAD".equals(tag)) {
                final Result<Map<String, String>, String> tagsToHash = scmLookup.getRefs(uri, Kind.TAG);
                if (tagsToHash.isFailure()) {
                    return AnnotatedFqScmRef.createFailed(gav.getVersion(), uri, tagsToHash.failure());
                }
                final ScmRef ref = guessTag(uri, gav, tagsToHash.result());
                if (ref != null) {
                    return new AnnotatedFqScmRef(ref, uri);
                } else {
                    final String msg = "Could not guess SCM ref for generic tag name " + tag + " of " + gav + " in " + uri;
                    return AnnotatedFqScmRef.createFailed(gav.getVersion(), uri, msg);
                }
            }
            return validateTag(uri, tag, gav.getVersion());
        } catch (Exception e) {
            final StringWriter sw = new StringWriter();
            final String msg = "Could not find SCM ref for " + gav + " in " + uri;
            sw.append(msg).append("\n");
            try (PrintWriter pw = new PrintWriter(sw)) {
                e.printStackTrace(pw);
            }
            return AnnotatedFqScmRef.createFailed(gav.getVersion(), uri, sw.toString());
        }
    }

}
