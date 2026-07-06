/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.maven;

import io.smallrye.mutiny.Multi;
import io.vertx.mutiny.core.file.FileSystem;
import java.nio.file.Path;
import java.util.Iterator;
import org.l2x6.pom.tuner.model.Gav;
import org.l2x6.pom.tuner.model.Gavtc.Type;
import org.l2x6.pom.tuner.model.Gavtcf;

public class LocalMavenRepository {
    private final Path rootDirectory;
    private final FileSystem fileSystem;

    private LocalMavenRepository(Path rootDirectory, FileSystem fileSystem) {
        this.rootDirectory = rootDirectory;
        this.fileSystem = fileSystem;
    }

    public Multi<Gavtcf> gavtcfStream() {
        return walkDirectories(rootDirectory.toString())
                .onItem().transformToMultiAndMerge(dirPath -> VersionDirectory.of(rootDirectory, Path.of(dirPath), fileSystem))
                .onItem().transformToMultiAndMerge(VersionDirectory::artifacts);
    }

    private Multi<String> walkDirectories(String dir) {
        return fileSystem.readDir(dir)
                .onItem().transformToMulti(files -> Multi.createFrom().iterable(files))
                .onItem().transformToMultiAndMerge(file -> fileSystem.props(file)
                        .onItem().transformToMulti(props -> {
                            if (props.isDirectory()) {
                                return Multi.createBy().merging().streams(
                                        Multi.createFrom().item(file),
                                        walkDirectories(file));
                            }
                            return Multi.createFrom().empty();
                        }));
    }

    static class VersionDirectory {

        static Multi<VersionDirectory> of(Path rootDirectory, Path versionDirectory, FileSystem fileSystem) {
            final Path relDir = rootDirectory.relativize(versionDirectory);
            String version = relDir.getFileName().toString();
            final Path artifactDir = relDir.getParent();
            if (artifactDir == null) {
                return Multi.createFrom().empty();
            }
            String artifactId = artifactDir.getFileName().toString();
            final Path groupDir = artifactDir.getParent();
            if (groupDir == null) {
                return Multi.createFrom().empty();
            }
            String pomFile = versionDirectory.resolve(artifactId + "-" + version + ".pom").toString();
            return fileSystem.exists(pomFile)
                    .onItem().transformToMulti(exists -> {
                        if (!exists) {
                            return Multi.createFrom().empty();
                        }
                        Iterator<Path> it = groupDir.iterator();
                        final StringBuilder groupId = new StringBuilder(it.next().toString());
                        while (it.hasNext()) {
                            groupId.append('.').append(it.next().toString());
                        }
                        return Multi.createFrom().item(
                                new VersionDirectory(
                                        versionDirectory,
                                        new Gav(groupId.toString(), artifactId, version),
                                        fileSystem));
                    });
        }

        private VersionDirectory(Path absPath, Gav gav, FileSystem fileSystem) {
            this.absVersionDir = absPath;
            this.gav = gav;
            this.fileSystem = fileSystem;
        }

        private final Path absVersionDir;
        private final Gav gav;
        private final FileSystem fileSystem;

        public Multi<Gavtcf> artifacts() {
            String prefix = gav.getArtifactId() + "-" + gav.getVersion();
            return fileSystem.readDir(absVersionDir.toString())
                    .onItem().transformToMulti(files -> Multi.createFrom().iterable(files))
                    .onItem().transform(Path::of)
                    .select().where(file -> {
                        String fileName = file.getFileName().toString();
                        return fileName.startsWith(prefix)
                                && !fileName.endsWith(".asc")
                                && !fileName.endsWith(".md5")
                                && !fileName.endsWith(".sha1");
                    })
                    .onItem().transform(file -> {
                        String fileName = file.getFileName().toString();
                        final int lastPeriodPos = fileName.lastIndexOf('.');
                        final String type = fileName.substring(lastPeriodPos + 1);
                        final String classifier = (prefix.length() == lastPeriodPos)
                                ? null
                                : fileName.substring(prefix.length() + 1, lastPeriodPos);
                        return gav.toGavtc(Type.of(type), classifier)
                                .toGavtcf(absVersionDir.resolve(file.getFileName()));
                    });
        }
    }

}
