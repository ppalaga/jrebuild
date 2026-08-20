/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.build;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.l2x6.jrebuild.api.os.Eol;
import org.l2x6.jrebuild.api.os.Os;
import org.l2x6.pom.tuner.model.Gavtcf;

public interface Resource {
    /**
     * @return a string describing the location of the resource, a filesystem path or a similar identifier
     */
    String location();

    /**
     * @return a possibly cached lower case (with {@code ROOT} locale) variant of {@link #location()}
     */
    String lowerCaseLocation();

    boolean isMissing();

    <T> T as(Class<T> cl);

    byte[] bytes();

    String string();

    default List<Line> lines() {
        List<Line> lines = new ArrayList<>();
        try (BufferedReader r = openReader()) {
            StringBuilder sb = new StringBuilder();
            int ch;
            while ((ch = r.read()) != -1) {
                if (ch == '\r') {
                    r.mark(1);
                    if (r.read() == '\n') {
                        lines.add(new Line(sb.toString(), Eol.CRLF));
                        sb.setLength(0);
                        continue;
                    } else {
                        lines.add(new Line(sb.toString(), Eol.CR));
                        sb.setLength(0);
                        r.reset();
                        continue;
                    }
                } else if (ch == '\n') {
                    lines.add(new Line(sb.toString(), Eol.CR));
                    sb.setLength(0);
                    continue;
                }
                sb.append((char) ch);
            }
            if (sb.length() > 0) {
                lines.add(new Line(sb.toString(), null));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + location(), e);
        }
        return Collections.unmodifiableList(lines);
    }

    BufferedReader openReader() throws IOException;

    public class FileResource implements Resource {

        private final Path file;
        private final String location;
        private String lowerCaseLocation;

        public FileResource(Path file, String location) {
            super();
            this.file = file;
            if (Os.current() == Os.WINDOWS) {
                this.location = location.replace('\\', '/');
            } else {
                this.location = location;
            }
        }

        @Override
        public String location() {
            return location;
        }

        @Override
        public <T> T as(Class<T> cl) {
            if (cl == ZipFile.class) {
                try {
                    return (T) ZipFile.builder()
                            .setFile(file.toFile())
                            .get();
                } catch (IOException e) {
                    throw new UncheckedIOException("Could not read " + file, e);
                }
            }
            return null;
        }

        @Override
        public byte[] bytes() {
            try {
                return Files.readAllBytes(file);
            } catch (IOException e) {
                throw new UncheckedIOException("Could not read " + file, e);
            }
        }

        @Override
        public String string() {
            return new String(bytes(), StandardCharsets.UTF_8);
        }

        @Override
        public boolean isMissing() {
            return !Files.exists(file);
        }

        public BufferedReader openReader() throws IOException {
            return Files.newBufferedReader(file, StandardCharsets.UTF_8);
        }

        @Override
        public String lowerCaseLocation() {
            String lcLoc = lowerCaseLocation;
            if (lcLoc == null) {
                lcLoc = lowerCaseLocation = location.toLowerCase(Locale.ROOT);
            }
            return lowerCaseLocation;
        }

    }

    public class ByteArrayResource implements Resource {
        private static final Resource MISSING = new ByteArrayResource(null, null);

        public static Resource missing() {
            return MISSING;
        }

        private final byte[] bytes;
        private volatile String string;
        private final Object stringLock = new Object();
        private final String location;
        private String lowerCaseLocation;

        ByteArrayResource(String path, byte[] bytes) {
            super();
            this.location = path;
            this.bytes = bytes;
        }

        public String location() {
            return location;
        }

        @Override
        public String lowerCaseLocation() {
            String lcLoc = lowerCaseLocation;
            if (lcLoc == null) {
                lcLoc = lowerCaseLocation = location.toLowerCase(Locale.ROOT);
            }
            return lowerCaseLocation;
        }

        public byte[] bytes() {
            return bytes;
        }

        public String string() {
            String s;
            if ((s = string) == null) {
                synchronized (stringLock) {
                    if ((s = string) == null) {
                        s = string = new String(bytes, StandardCharsets.UTF_8);
                    }
                }
            }
            return s;
        }

        @Override
        public boolean isMissing() {
            return bytes == null;
        }

        @Override
        public <T> T as(Class<T> cl) {
            if (cl == ZipFile.class) {
                try {
                    return (T) ZipFile.builder()
                            .setByteArray(bytes)
                            .get();
                } catch (IOException e) {
                    throw new UncheckedIOException("Could not read " + location, e);
                }
            }
            return null;
        }

        @Override
        public BufferedReader openReader() throws IOException {
            return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8));
        }
    }

    public static Resource of(Gavtcf gavtcf) {
        return new FileResource(gavtcf.getFile(), gavtcf.toGavtc().getRepositoryPath());
    }

    public static Resource of(String path, byte[] bytes) {
        return new ByteArrayResource(path, bytes);
    }

    public static Resource of(String path, String content) {
        return new ByteArrayResource(path, content.getBytes(StandardCharsets.UTF_8));
    }

    public record Line(String line, Eol eol) {

        @Override
        public String toString() {
            return eol == null ? line : line + eol.escapedEolString();
        }

    }

}
