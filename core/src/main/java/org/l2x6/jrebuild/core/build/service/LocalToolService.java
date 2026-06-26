package org.l2x6.jrebuild.core.build.service;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.cliassured.sdkman.InstalledCandidate;
import org.cliassured.sdkman.Sdk;
import org.cliassured.sdkman.SdkSpec;
import org.l2x6.jrebuild.api.os.Os;
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
            case "sdkman": {
                p = new Sdkman(cacheDir.resolve("sdkman"));
                break;
            }
            default:
                throw new IllegalArgumentException("Unexpected packager: " + k);
            }
            ;
            return new PackagerEntry(p, new ConcurrentHashMap<>());
        });
        String toolKey = tool.name() + "-" + tool.versionDistribution();
        return packagerEntry.installedTools.computeIfAbsent(toolKey, k -> packagerEntry.packager.install(tool));
    }

    static record PackagerEntry(Packager packager, Map<String, InstalledTool> installedTools) {
    }

    public static class Sdkman implements Packager {

        private final SdkSpec sdk;

        public Sdkman(Path cacheDir) {
            super();
            sdk = Sdk.home(cacheDir).installSdkmanIfNeeded();
        }

        @Override
        public InstalledTool install(Tool tool) {
            final String versionDist = tool.version() + (tool.distribution() != null ? ("-" + tool.distribution()) : "");
            InstalledCandidate installedTool = sdk.installIfNeeded(tool.name(), versionDist);
            String binName = tool.executable() + Os.current().executableSuffix();
            Path toolExecutable = installedTool.findBinary(binName)
                    .orElseThrow(
                            () -> new IllegalStateException("Could not find " + binName + " in " + installedTool.bin()));
            ;
            return new InstalledTool(tool, toolExecutable);
        }

        @Override
        public String name() {
            return "sdkman";
        }
    }

}
