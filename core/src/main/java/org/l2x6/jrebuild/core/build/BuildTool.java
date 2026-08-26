/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.build;

import java.util.Optional;
import org.l2x6.jrebuild.api.os.Tool;
import org.l2x6.jrebuild.api.util.ComparableVersion;
import org.l2x6.jrebuild.core.build.service.LocalToolService.CliAssuredPackager;
import org.l2x6.jrebuild.core.build.service.LocalToolService.Sdkman;

public enum BuildTool {
    maven_wrapper(true, "./mvnw"),
    gradle_wrapper(true, "./gradlew"),
    maven(false, "mvn") {
        public Optional<Tool> tool(String version) {
            return Optional.of(CliAssuredPackager.maven(version));
        }
    },
    gradle(false, "gradle") {
        public Optional<Tool> tool(String version) {
            return Optional.of(Sdkman.gradle(version));
        }
    },
    ant(false, "ant") {
        public Optional<Tool> tool(String version) {
            return Optional.of(Sdkman.ant(version));
        }
    };

    private final boolean wrapper;
    private final String command;

    BuildTool(boolean wrapper, String command) {
        this.wrapper = wrapper;
        this.command = command;
    }

    public String command() {
        return command;
    }

    public boolean isWrapper() {
        return wrapper;
    }

    public Optional<Tool> tool(String version) {
        return Optional.empty();
    }

    public BuildToolAndVersion version(String version) {
        return new BuildToolAndVersion(this, new ComparableVersion(version));
    }

}
