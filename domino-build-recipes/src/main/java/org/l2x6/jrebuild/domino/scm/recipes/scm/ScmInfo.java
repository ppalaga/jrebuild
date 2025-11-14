/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.domino.scm.recipes.scm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ScmInfo extends RepositoryInfo {
    public ScmInfo() {
    }

    public ScmInfo(String type, String uri) {
        super(type, uri);
    }

    public ScmInfo(String type, String uri, String path) {
        super(type, uri, path);
    }

    private List<RepositoryInfo> legacyRepos = new ArrayList<>();

    public List<RepositoryInfo> getLegacyRepos() {
        return legacyRepos;
    }

    public ScmInfo setLegacyRepos(List<RepositoryInfo> legacyRepos) {
        this.legacyRepos = legacyRepos;
        return this;
    }
}
