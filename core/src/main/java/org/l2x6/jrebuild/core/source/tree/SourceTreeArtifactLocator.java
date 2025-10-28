/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.source.tree;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;
import org.l2x6.pom.tuner.MavenSourceTree;
import org.l2x6.pom.tuner.model.Gavtcs;
import org.l2x6.pom.tuner.model.Module;

public class SourceTreeArtifactLocator {

    private final Predicate<String> activeProfiles;
    private final Path projectDirectory;
    private volatile MavenSourceTree mavenSourceTree;
    private final Object lock = new Object();

    public SourceTreeArtifactLocator(Path projectDirectory, Predicate<String> activeProfiles) {
        this.projectDirectory = projectDirectory;
        this.activeProfiles = activeProfiles;
    }

    public Path findArtifact(Gavtcs gavtcs) {
        if (projectDirectory == null) {
            return null;
        }
        if (mavenSourceTree == null) {
            synchronized (lock) {
                if (mavenSourceTree == null) {
                    mavenSourceTree = MavenSourceTree.of(
                            projectDirectory.resolve("pom.xml"),
                            StandardCharsets.UTF_8,
                            dep -> false,
                            p -> activeProfiles.test(p.getId()));
                }
            }
        }
        final Module module = mavenSourceTree.getModulesByGa().get(gavtcs.toGa());
        if (module != null) {
            if ("pom".equals(gavtcs.getType())) {
                return projectDirectory.resolve(module.getPomPath());
            }
            Path p = projectDirectory
                    .resolve(module.getPomPath()).getParent()
                    .resolve("target").resolve(getFileName(gavtcs));
            if (Files.isRegularFile(p)) {
                return p;
            }
        }
        return null;
    }

    static String getFileName(Gavtcs artifact) {
        StringBuilder fileName = new StringBuilder();
        fileName.append(artifact.getArtifactId()).append('-').append(artifact.getVersion());
        if (!artifact.getClassifier().isEmpty()) {
            fileName.append('-').append(artifact.getClassifier());
        }
        fileName.append('.');
        String type = artifact.getType();
        if (type == null) {
            fileName.append("jar");
        } else {
            fileName.append(type);
        }
        return fileName.toString();
    }

}
