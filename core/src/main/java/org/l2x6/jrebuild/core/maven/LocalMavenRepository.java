package org.l2x6.jrebuild.core.maven;

import java.nio.file.Path;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Iterator;
import java.util.Optional;
import java.util.stream.Stream;
import org.l2x6.pom.tuner.model.Gav;
import org.l2x6.pom.tuner.model.Gavtc.Type;
import org.l2x6.pom.tuner.model.Gavtcf;
public class LocalMavenRepository  {
    private final Path rootDirectory;

    private LocalMavenRepository(Path rootDirectory) {
        this.rootDirectory = rootDirectory;
    }

    public Stream<Gavtcf> gavtcfStream() {
        try {
            return Files.walk(rootDirectory).filter(Files::isDirectory)
                    .map(versionDir -> VersionDirectory.of(rootDirectory, versionDir))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .flatMap(VersionDirectory::artifacts);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not walk " + rootDirectory, e);
        }
    }

    static class VersionDirectory {

        static Optional<VersionDirectory> of(Path rootDirectory, Path versionDirectory) {
            final Path relDir = rootDirectory.relativize(versionDirectory);
            String version = relDir.getFileName().toString();
            final Path artifactDir = relDir.getParent();
            if (artifactDir == null) {
                return Optional.empty();
            }
            String artifactId = artifactDir.getFileName().toString();
            if (Files.isRegularFile(versionDirectory.resolve(artifactId + "-" + version + ".pom"))) {
                final Path groupDir = artifactDir.getParent();
                if (groupDir == null) {
                    return Optional.empty();
                }
                Iterator<Path> it = groupDir.iterator();
                final StringBuilder groupId = new StringBuilder(it.next().toString());
                while (it.hasNext()) {
                    groupId.append('.').append(it.next().toString());
                }
                return Optional
                        .of(new VersionDirectory(
                                versionDirectory,
                                new Gav(groupId.toString(), artifactId, version)));
            }
            return Optional.empty();
        }

        private VersionDirectory(Path absPath, Gav gav) {
            this.absVersionDir = absPath;
            this.gav = gav;
        }

        private final Path absVersionDir;
        private final Gav gav;

        public Stream<Gavtcf> artifacts() {
            try {
                String prefix = gav.getArtifactId() + "-" + gav.getVersion();
                return Files.list(absVersionDir).filter(file -> {
                    String fileName = file.getFileName().toString();
                    return fileName.startsWith(prefix)
                            && !fileName.endsWith(".asc")
                            && !fileName.endsWith(".md5")
                            && !fileName.endsWith(".sha1");
                })
                        .map(file -> {
                            String fileName = file.getFileName().toString();
                            final int lastPeriodPos = fileName.lastIndexOf('.');
                            final String type = fileName.substring(lastPeriodPos + 1);
                            final String classifier = (prefix.length() == lastPeriodPos)
                                    ? null
                                    : fileName.substring(prefix.length() + 1, lastPeriodPos);
                            return gav.toGavtc(Type.of(type), classifier)
                                    .toGavtcf(absVersionDir.resolve(file.getFileName()));
                        });
            } catch (IOException e) {
                throw new UncheckedIOException("Could not list " + absVersionDir, e);
            }
        }
    }

}
