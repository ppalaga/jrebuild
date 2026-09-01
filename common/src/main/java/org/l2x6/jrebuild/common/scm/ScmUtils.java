/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.common.scm;

import java.util.Set;
import org.l2x6.jrebuild.api.scm.ScmRepository.AnnotatedScmRepository;
import org.l2x6.jrebuild.api.scm.ScmRepository.ScmRepositoryType;
import org.l2x6.jrebuild.common.git.GitUtils;

public class ScmUtils {
    private static final String HTTPS_GITHUB_COM = "https://github.com/";

    private ScmUtils() {
    }

    public static AnnotatedScmRepository toNormalizedHttpsAnnotatedScmRepository(
            String source,
            ScmRepositoryType type,
            String uri) {
        uri = normalizeScmUri(uri);
        if (type == ScmRepositoryType.git) {
            uri = GitUtils.toNormalizedHttpsUri(uri);
        }
        return AnnotatedScmRepository.of(source, type, uri);
    }

    private static final Set<String> STRIP_PREFIXES = Set.of("scm:", "git:", "svn:", "//");

    static String normalizeScmUri(String s) {
        for (String prefix : STRIP_PREFIXES) {
            if (s.startsWith(prefix)) {
                s = s.substring(prefix.length());
            }
        }
        //        if (s.startsWith("http://")) {
        //            s = s.replace("http://", "https://");
        //        } else if (!s.startsWith("https://")) {
        //            s = s.replace(':', '/');
        //            if (s.startsWith("github.com:")) {
        //                s = s.replace(':', '/');
        //            }
        //            if (s.startsWith("//")) {
        //                s = "https:" + s;
        //            } else {
        //                s = "https://" + s;
        //            }
        //        }
        //        while (!s.startsWith("file://") && s.endsWith("/")) {
        //            s = s.substring(0, s.length() - 1);
        //        }
        return s;
    }
}
