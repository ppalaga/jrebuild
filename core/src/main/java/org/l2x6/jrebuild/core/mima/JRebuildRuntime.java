/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.mima;

import eu.maveniverse.maven.mima.context.Context;
import eu.maveniverse.maven.mima.context.ContextOverrides;
import eu.maveniverse.maven.mima.context.Runtime;
import eu.maveniverse.maven.mima.context.Runtimes;
import eu.maveniverse.maven.mima.context.internal.RuntimeSupport;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.WorkspaceReader;
import org.eclipse.aether.repository.WorkspaceRepository;
import org.l2x6.pom.tuner.MavenSourceTree;
import org.l2x6.pom.tuner.model.Ga;
import org.l2x6.pom.tuner.model.Module;
import org.l2x6.pom.tuner.model.Profile;

public class JRebuildRuntime implements Runtime {
    private final RuntimeSupport delegate;

    JRebuildRuntime(RuntimeSupport delegate) {
        super();
        this.delegate = delegate;
    }

    public String name() {
        return delegate.name();
    }

    public String version() {
        return delegate.version();
    }

    public int priority() {
        return delegate.priority();
    }

    public String mavenVersion() {
        return delegate.mavenVersion();
    }

    public boolean managedRepositorySystem() {
        return delegate.managedRepositorySystem();
    }

    public Context create(ContextOverrides overrides) {
        Path basedir = overrides.getBasedirOverride();
        if (basedir == null) {
            return delegate.create(overrides);
        } else {
            final Context ctx = delegate.create(overrides);
            final DefaultRepositorySystemSession session = new DefaultRepositorySystemSession(ctx.repositorySystemSession());
            final HashSet<String> profiles = new HashSet<String>(overrides.getActiveProfileIds());
            session.setWorkspaceReader(new JRebuildWorkspace(basedir, profiles::contains));
            session.setReadOnly();
            return new Context(
                    delegate,
                    overrides,
                    ctx.basedir(),
                    ctx.mavenUserHome(),
                    ctx.mavenSystemHome(),
                    ctx.repositorySystem(),
                    session,
                    ctx.remoteRepositories(),
                    ctx.httpProxy(),
                    ctx.lookup(),
                    ctx.repositorySystem()::shutdown);
        }
    }

    static class JRebuildWorkspace implements WorkspaceReader {
        private final Predicate<Profile> activeProfiles;
        private final Path projectDirectory;
        private volatile MavenSourceTree mavenSourceTree;
        private final Object lock = new Object();
        private final WorkspaceRepository repository = new WorkspaceRepository();

        public JRebuildWorkspace(Path projectDirectory, Predicate<String> activeProfiles) {
            this.projectDirectory = projectDirectory;
            this.activeProfiles = p -> p.getId() == null || activeProfiles.test(p.getId());
        }

        @Override
        public WorkspaceRepository getRepository() {
            return repository;
        }

        @Override
        public File findArtifact(Artifact artifact) {

            return findModule(artifact.getGroupId(), artifact.getArtifactId())
                    .map(m -> resolve(projectDirectory, artifact, m))
                    .orElse(null);
        }

        @Override
        public List<String> findVersions(Artifact artifact) {
            Optional<Module> module = findModule(artifact.getGroupId(), artifact.getArtifactId());
            Optional<String> version = module
                    .map(m -> {
                        synchronized (lock) {
                            return mavenSourceTree
                                    .getExpressionEvaluator(activeProfiles).evaluateGav(m.getGav())
                                    .getVersion();
                        }
                    });
            if (version.isPresent()) {
                return List.of(version.get());
            } else {
                return Collections.emptyList();
            }
        }

        Optional<Module> findModule(String groupId, String artifactId) {
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
                                activeProfiles);
                    }
                }
            }
            Module module = mavenSourceTree.getModulesByGa().get(new Ga(groupId, artifactId));
            return Optional.ofNullable(module);
        }

        static File resolve(Path projectDirectory, Artifact artifact, Module module) {
            if ("pom".equals(artifact.getExtension())) {
                return projectDirectory.resolve(module.getPomPath()).toFile();
            }
            Path p = projectDirectory
                    .resolve(module.getPomPath()).getParent()
                    .resolve("target").resolve(getFileName(artifact));
            if (Files.isRegularFile(p)) {
                return p.toFile();
            }
            return (File) null;
        }

        static String getFileName(Artifact artifact) {
            StringBuilder fileName = new StringBuilder();
            fileName.append(artifact.getArtifactId()).append('-').append(artifact.getVersion());
            if (!artifact.getClassifier().isEmpty()) {
                fileName.append('-').append(artifact.getClassifier());
            }
            fileName.append('.');
            String type = artifact.getExtension();
            if (type == null) {
                fileName.append("jar");
            } else {
                fileName.append(type);
            }
            return fileName.toString();
        }
    }

    public static Runtime getInstance() {
        return new JRebuildRuntime((RuntimeSupport) Runtimes.INSTANCE.getRuntime());
    }

}
