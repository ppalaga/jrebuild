/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.reproducible.central.api;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public record Buildspec(
        /* 1. what does this rebuild? */
        /** Central Repository coordinates for the Reference release (for multi-module, pick an artitrary module) */
        String groupId,
        /** Central Repository coordinates for the Reference release (for multi-module, pick an artitrary module) */
        String artifactId,
        /** Central Repository coordinates for the Reference release (for multi-module, pick an artitrary module) */
        String version,
        /** Where are reference binaries? */
        String referenceRepo,
        /**
         * (= gav to path in referenceRepo) is Maven repository: https://maven.apache.org/repositories/layout.html
         * (future options could be PyPI, npm, Brew, Dockerhub, ...)
         */
        String layout,

        /* 2. where is source code? */
        String gitRepo,
        String gitTag,
        /** or use source zip archive */
        String sourceDistribution,
        /** ${artifactId}-${version} */
        String sourcePath,
        String sourceRmFiles,

        /* 3. rebuild environment prerequisites */
        /** mvn, mvn-3.8.5, gradle, sbt */
        String tool,
        /** 8 */
        String jdk,
        String toolchains,
        /** crlf for Windows, lf for Unix */
        Newline newline,
        /** optional */
        String umask,
        /** Etc/GMT */
        String timezone,
        /** en_US */
        String locale,

        /** if rebuild output depends on OS and/or processor architecture */
        String os,
        String arch,

        /**
         * if rebuild output depends on Azul JDK even when default OpenJDK is available, like with JDK 8 and
         * cyclonedx-maven-plugin
         */
        boolean jdkForceAzul,
        /** if rebuild output depends on working directory where source code is stored: /var/<tool>/app */
        String workdir,

        /* 4. rebuild command */
        /** mvn -Papache-release clean package -DskipTests -Dmaven.javadoc.skip -Dgpg.skip */
        String command,
        /** optional */
        String execBefore,
        /** optional */
        String execAfter,

        /* 5. location of the buildinfo file generated during rebuild to record output fingerprints */
        /** target/${artifactId}-${version}.buildinfo */
        String buildinfo,

        /* 6. if the release is finally not reproducible, link to an issue tracker entry if one was created */
        /** ${artifactId}-${version}.diffoscope */
        String diffoscope,
        /** https://github.com/project_org/${artifactId}/issues/xx */
        String issue) {

    public static Buildspec of(Path file) {
        try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
            Builder b = new Builder();
            lines.forEach(b::line);
            return b.build();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + file, e);
        } catch (Exception e) {
            throw new RuntimeException("Could not parse " + file + ": " + e.getMessage(), e);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static enum Newline {
        lf("lf"), crlf("crlf"), crlf_nogit("crlf-nogit");

        private final String literal;

        private Newline(String literal) {
            this.literal = literal;
        }

        public String toString() {
            return literal;
        }

        public static Newline of(String literal) {
            return switch (literal) {
            case "lf" -> lf;
            case "crlf" -> crlf;
            case "crlf,no-auto.crlf" -> crlf; // TODO https://github.com/jvm-repo-rebuild/reproducible-central/issues/4556
            case "crlf-nogit" -> crlf_nogit; // TODO https://github.com/jvm-repo-rebuild/reproducible-central/issues/4556
            default -> throw new IllegalArgumentException("Unexpected " + Newline.class.getName() + " value: " + literal);
            };
        }
    }

    public static class Builder {

        private Map<String, String> values = new LinkedHashMap<>();
        private int pos = 0;
        private String src;

        private String identifier() {
            if (pos >= src.length()) {
                throw new IllegalStateException(
                        "Unexpected end of input in " + src + "; expected identifier start [A-Za-z_]");
            }
            int start = pos;
            char ch = src.charAt(pos);
            if (!(ch == '_'
                    || (ch >= 'a' && ch <= 'z')
                    || (ch >= 'A' && ch <= 'Z'))) {
                throw new IllegalStateException(
                        "Unexpected character '" + ch + "' at index " + pos + " in '" + src
                                + "'; expected identifier start [A-Za-z_]");
            }
            pos++;
            while (pos < src.length()) {
                ch = src.charAt(pos);
                if (!(ch == '_'
                        || (ch >= '0' && ch <= '9')
                        || (ch >= 'a' && ch <= 'z')
                        || (ch >= 'A' && ch <= 'Z'))) {
                    break;
                }
                pos++;
            }
            return src.substring(start, pos);
        }

        private void consume(char c) {
            if (pos >= src.length()) {
                throw new IllegalStateException("Unexpected end of input in " + src + "; expected " + c);
            }
            char ch;
            if ((ch = src.charAt(pos)) != c) {
                throw new IllegalStateException(
                        "Unexpected character '" + ch + "' at index " + pos + " in '" + src + "'; expected " + c);
            }
            pos++;
        }

        public Builder() {
            values.put("locale", "en_US");
            values.put("timezone", "UTC");
            values.put("umask", "0002");
            values.put("referenceRepo", "https://repo.maven.apache.org/maven2/");

        }

        public Builder line(String line) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                return this;
            }
            src = src == null ? line : src + "\n" + line;
            String key = identifier();
            consume('=');
            String val = src.substring(pos);
            int nlPos = lastIndexOf(val, '\n', 0, val.length(), 0);
            int quotePos = lastIndexOf(val, '"', nlPos, val.length(), nlPos);
            int hashPos = lastIndexOf(val, '#', quotePos, val.length(), -1);
            if (hashPos >= 0) {
                if (hashPos - 1 >= 0) {
                    char preceding = val.charAt(hashPos - 1);
                    if (preceding == ' ' || preceding == '\t') {
                        val = val.substring(0, hashPos - 1).trim();
                    }
                }
            }
            if (val.startsWith("\"") && !val.endsWith("\"")) {
                /* Unfinished multiline string */
                pos = 0;
                return this;
            }
            if (val.startsWith("\"") && val.endsWith("\"")) {
                val = val.substring(1, val.length() - 1).replace("\\\\", "\\").replace("\\\"", "\"");
            }
            values.put(key, val);
            src = null;
            pos = 0;
            return this;
        }

        void assertFinished() {
            if (src != null) {
                throw new IllegalStateException("Unparsed input at index " + pos + " in '" + src + "'");
            }
        }

        private int lastIndexOf(String val, char c, int start, int end, int defaultResult) {
            end--;
            while (end >= start) {
                if (val.charAt(end) == c) {
                    return end;
                }
                end--;
            }
            return defaultResult;
        }

        public Buildspec build() {
            assertFinished();

            final String groupId = resolveMandatory("groupId");
            final String artifactId = resolveMandatory("artifactId");
            final String version = resolveMandatory("version");
            final String referenceRepo = resolve("referenceRepo");
            final String layout = resolve("layout");
            final String sourceDistribution = resolve("sourceDistribution");
            final String gitRepo;
            final String gitTag;
            if (sourceDistribution == null) {
                gitRepo = resolveMandatory("gitRepo");
                gitTag = resolveMandatory("gitTag");
            } else {
                gitRepo = null;
                gitTag = null;
            }
            final String sourcePath = resolve("sourcePath");
            final String sourceRmFiles = resolve("sourceRmFiles");
            final String tool = resolveMandatory("tool");
            final String jdk = resolveMandatory("jdk");
            final String toolchains = resolve("toolchains");
            final Newline newline = Newline.of(resolveMandatory("newline"));
            final String umask = resolve("umask");
            final String timezone = resolve("timezone");
            final String locale = resolve("locale");
            final String os = resolve("os");
            final String arch = resolve("arch");
            final boolean jdkForceAzul = Boolean.parseBoolean(resolve("jdkForceAzul"));
            final String workdir = resolve("workdir");
            final String command = resolveMandatory("command");
            final String execBefore = resolve("execBefore");
            final String execAfter = resolve("execAfter");
            final String buildinfo = resolveMandatory("buildinfo");
            final String diffoscope = resolve("diffoscope");
            final String issue = resolve("issue");

            return new Buildspec(groupId, artifactId, version, referenceRepo, layout, gitRepo, gitTag, sourceDistribution,
                    sourcePath, sourceRmFiles, tool, jdk, toolchains, newline, umask, timezone, locale, os, arch, jdkForceAzul,
                    workdir, command, execBefore, execAfter, buildinfo, diffoscope, issue);
        }

        private static final Pattern BRACED_EXPRESSION_PATTERN = Pattern.compile("\\$\\{([^\\}]+)\\}");
        private static final Pattern SIMPLE_EXPRESSION_PATTERN = Pattern.compile("\\$([a-zA-Z_][0-9a-zA-Z_]*)");

        private String resolveMandatory(String key) {
            String result = resolve(key);
            if (result == null) {
                throw new IllegalStateException("Missing required key " + key);
            }
            return result;
        }

        String resolve(String key) {
            final String val = values.get(key);
            if (val == null) {
                return null;
            }
            CharSequence src = replace(BRACED_EXPRESSION_PATTERN, val);
            src = replace(SIMPLE_EXPRESSION_PATTERN, src);
            final String newVal = src.toString();
            values.put(key, newVal);
            return newVal;
        }

        private StringBuilder replace(Pattern pattern, CharSequence val) {
            StringBuilder sb = new StringBuilder();
            {
                Matcher m = pattern.matcher(val);
                while (m.find()) {
                    String k = m.group(1);
                    final String v = values.get(k);
                    if (v == null) {
                        throw new IllegalStateException("Unresolved expression ${" + k + "} in " + val);
                    }
                    m.appendReplacement(sb, v.replace("\\", "\\\\").replace("$", "\\$"));
                }
                m.appendTail(sb);
            }
            return sb;
        }

    }
}
