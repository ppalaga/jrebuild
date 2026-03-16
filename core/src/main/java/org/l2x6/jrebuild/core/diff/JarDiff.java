package org.l2x6.jrebuild.core.diff;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.l2x6.jrebuild.core.build.Reproducibility;
import org.l2x6.jrebuild.core.build.ResourceMatch;
import org.l2x6.jrebuild.core.mvn.MavenUtils;
import org.l2x6.pom.tuner.model.Gavtc;

public record JarDiff(
        Path localMavenRepo,
        List<RemoteRepository> repositories,
        RepositorySystem repoSystem,
        RepositorySystemSession repoSession
        ) {


    public DiffResult diff(Gavtc gavtc, Path rebuiltJarFile) {

        final Path refJarFile = MavenUtils.resolveArtifact(
                localMavenRepo,
                gavtc,
                repositories,
                repoSystem,
                repoSession);

        return diff(gavtc, refJarFile, rebuiltJarFile);

    }

    static DiffResult diff(Gavtc gavtc, Path refJarFile, Path rebuiltJarFile) {

        if (binaryEqual(refJarFile, rebuiltJarFile)) {
            return DiffResult.perfect();
        }


        try (ZipFile refZip = ZipFile.builder()
                .setFile(refJarFile.toFile())
                .get();
                ZipFile rebuiltZip = ZipFile.builder()
                        .setFile(rebuiltJarFile.toFile())
                        .get()
                ) {

            final Enumeration<ZipArchiveEntry> refEntries = refZip.getEntries();
            final Enumeration<ZipArchiveEntry> rebuiltEntries = rebuiltZip.getEntries();

            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read from " + refJarFile, e);
        }

    }
    static class IndexedZip implements AutoCloseable {
        private final Path file;
        private final ZipFile zipFile;
        private final Map<String, ZipArchiveEntry> entries;
        private final Set<String> classFiles;
        private final Set<String> nonClassFiles;

        public IndexedZip(Path file) {
            super();
            this.file = file;
            try {
                this.zipFile = ZipFile.builder()
                        .setFile(file.toFile())
                        .get();
                Map<String, ZipArchiveEntry> map = new TreeMap<>();
                Set<String> clsFiles = new TreeSet<>();
                Set<String> nonClsFiles = new TreeSet<>();
                Enumeration<ZipArchiveEntry> es = zipFile.getEntries();
                while (es.hasMoreElements()) {
                    ZipArchiveEntry entry = es.nextElement();
                    String name = entry.getName();
                    map.put(name, entry);
                    if (name.endsWith(".class")) {
                        clsFiles.add(name);
                    } else {
                        nonClsFiles.add(name);
                    }
                }
                this.entries = Map.copyOf(map);
                this.classFiles = Set.copyOf(clsFiles);
                this.nonClassFiles = Set.copyOf(nonClsFiles);
            } catch (IOException e) {
                throw new UncheckedIOException("Could not read "+ file, e);
            }
        }

        public DiffResult diff(IndexedZip other) {
            Map<String, ClassFileDiff> classFileDiffs = classFileDiffs(other);
            Map<String, FileDiff> nonClassFileDiffs = nonClassFileDiffs(other);
            ResourceMatch match = ResourceMatch.PERFECT;
            for (ClassFileDiff diff : classFileDiffs.values()) {
                if (diff.resourceMatch.ordinal() < match.ordinal()) {
                    match = diff.resourceMatch;
                }
            }
            for (FileDiff diff : nonClassFileDiffs.values()) {
                if (diff.resourceMatch.ordinal() < match.ordinal()) {
                    match = diff.resourceMatch;
                }
            }
            return new DiffResult(match, classFileDiffs, nonClassFileDiffs);
        }

        Map<String, FileDiff> nonClassFileDiffs(IndexedZip other) {
            return null;
        }

        Map<String, ClassFileDiff> classFileDiffs(IndexedZip other) {
            Map<String, ClassFileDiff> result = new TreeMap<>();
            Set<String> otherCopy = new TreeSet<>(other.classFiles);
            Iterator<String> it = this.classFiles.iterator();
            while (it.hasNext()) {
                String path = it.next();
                if (otherCopy.remove(path)) {
                    /* Compare class file */
                    List<ClassFileDelta> diffs = diffClass(
                            this.getBytes(path),
                            other.getBytes(path));
                    if (diffs.isEmpty()) {

                    }
                } else {

                }
            }
            return Map.copyOf(result);
        }

        private byte[] getBytes(String path) throws IOException {
            return this.zipFile.getInputStream(this.zipFile.getEntry(path)).readAllBytes();
        }

        static List<ClassFileDelta> diffClass(byte[] ref, byte[] rebuilt) {
            return null;
        }

        @Override
        public void close() throws Exception {
            this.zipFile.close();
        }

    }

    static class Buffer implements AutoCloseable {
        public Buffer(Path file) {
            super();
            this.file = file;
            try {
                this.in = Files.newInputStream(file);
            } catch (IOException e) {
                throw new UncheckedIOException("Could not open " + file, e);
            }
            this.buffer = new byte[8192];
        }

        private final Path file;
        private final InputStream in;
        private final byte[] buffer;
        private int length;
        private boolean atEndOfFile;

        void read() {
            length = 0;
            try {
                while (length < buffer.length && !atEndOfFile) {
                    int len = in.read(buffer, length, buffer.length - length);
                    if (len < 0) {
                        atEndOfFile = true;
                    } else {
                        length += len;
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Could not read from "+ file, e);
            }
        }

        public boolean bytesEqual(Buffer other) {

            while (true) {
                this.read();
                other.read();
                if (this.length != other.length) {
                    return false;
                }
                if (!Arrays.equals(
                        this.buffer, 0, this.length,
                        other.buffer, 0, other.length)) {
                    return false;
                }
                if (this.atEndOfFile != other.atEndOfFile) {
                    return false;
                }
                if (this.atEndOfFile && other.atEndOfFile) {
                    return true;
                }
                /* both not at end of file -> continue */
            }

        }

        public void close() {
            try {
                in.close();
            } catch (IOException e) {
                throw new UncheckedIOException("Could not close "+ file, e);
            }
        }
    }

    static boolean binaryEqual(Path refJarFile, Path rebuiltJarFile) {
        try (Buffer refIn = new Buffer(refJarFile);
                Buffer rebuiltIn = new Buffer(rebuiltJarFile)) {
            return refIn.bytesEqual(rebuiltIn);
        }
    }

    static record DiffResult(
            ResourceMatch resourceMatch,
            Map<String, ClassFileDiff> classFileDiffs,
            Map<String, FileDiff> nonClassFileDiffs
            ) {
        public static DiffResult perfect() {
            return new DiffResult(ResourceMatch.PERFECT, Map.of(), Map.of());
        }}

    static record FileDiff(ResourceMatch resourceMatch) {

    }

}
