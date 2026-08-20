/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.build.service;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.cliassured.maven.InstalledMaven;
import org.cliassured.maven.Maven;
import org.cliassured.sdkman.InstalledCandidate;
import org.cliassured.sdkman.Sdk;
import org.cliassured.sdkman.SdkmanSpec;
import org.l2x6.jrebuild.api.os.Packager;
import org.l2x6.jrebuild.api.os.Tool;
import org.l2x6.jrebuild.api.os.Tool.InstalledTool;

public class LocalToolService {
    private final Map<String, PackagerEntry> installedPackagers = new ConcurrentHashMap<>();
    private final Path cacheDir;

    public LocalToolService(Path cacheDir) {
        super();
        this.cacheDir = cacheDir;
    }

    public InstalledTool install(Tool tool) {
        String packagerName = tool.packagerName();

        final PackagerEntry packagerEntry = installedPackagers.computeIfAbsent(packagerName, k -> {
            Packager p = null;
            switch (k) {
            case Sdkman.NAME: {
                SdkmanSpec defaultSdkMan = org.cliassured.sdkman.Sdkman.given();
                // Reuse ~/.sdkman if available to speedup local tests
                p = new Sdkman(defaultSdkMan.isInstalled() ? defaultSdkMan.home() : cacheDir.resolve("sdkman"));
                break;
            }
            case CliAssuredPackager.NAME: {
                p = new CliAssuredPackager();
                break;
            }
            default:
                throw new IllegalArgumentException("Unexpected packager: " + k);
            }
            return new PackagerEntry(p, new ConcurrentHashMap<>());
        });
        String toolKey = tool.name() + "-" + tool.version();
        return packagerEntry.installedTools.computeIfAbsent(toolKey, k -> packagerEntry.packager.install(tool));
    }

    static record PackagerEntry(Packager packager, Map<String, InstalledTool> installedTools) {
    }

    public static class CliAssuredPackager implements Packager {

        private static final String NAME = "cli-assured";

        @Override
        public String name() {
            return NAME;
        }

        @Override
        public InstalledTool install(Tool tool) {
            switch (tool.name()) {
            case "maven": {
                InstalledMaven installedMaven = Maven.version(tool.version()).installIfNeeded();
                final Path binDir = installedMaven.home().resolve("bin");
                String toolHomevarName = tool.name().toUpperCase(Locale.ROOT) + "_HOME";
                return new InstalledTool(tool, List.of(binDir.toString()), toolHomevarName,
                        installedMaven.home().toString());
            }
            default:
                throw new IllegalArgumentException("Unexpected tool name for cli-assured packager: " + tool.name());
            }
        }

        public static Tool maven(String version) {
            return new Tool(NAME, "maven", version);
        }

    }

    public static class Sdkman implements Packager {

        public static final String NAME = "sdkman";
        private final Sdk sdk;

        public static Tool gradle(String version) {
            return new Tool(NAME, "gradle", version);
        }

        public static Tool ant(String version) {
            return new Tool(NAME, "ant", version);
        }

        public Sdkman(Path cacheDir) {
            super();
            sdk = org.cliassured.sdkman.Sdkman.home(cacheDir).installIfNeeded().sdk();
        }

        @Override
        public InstalledTool install(Tool tool) {
            InstalledCandidate installedTool = sdk.installCandidateIfNeeded(tool.name(), tool.version());
            final Path binDir = installedTool.home().resolve("bin");
            String toolHomevarName = tool.name().toUpperCase(Locale.ROOT) + "_HOME";
            return new InstalledTool(tool, List.of(binDir.toString()), toolHomevarName, installedTool.home().toString());
        }

        @Override
        public String name() {
            return "sdkman";
        }
    }

}
