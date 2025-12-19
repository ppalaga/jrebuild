/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.reproducible.central.api;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jboss.logging.Logger;
import org.l2x6.cli.assured.CliAssured;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public record Buildspec(
        /* 1. what does this rebuild? */
        /** Central Repository coordinates for the Reference release (for multi-module, pick an artitrary module) */
        String groupId,
        /** Central Repository coordinates for the Reference release (for multi-module, pick an artitrary module) */
        String artifactId,
        /** Central Repository coordinates for the Reference release (for multi-module, pick an artitrary module) */
        String version,
        String display,
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
        /** Set this if Git content newline does not match the runtime newline */
        Newline newlineGit,
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
    private static final Logger log = Logger.getLogger(Buildspec.class);

    public static Buildspec of(Path file) {
        try {
            return of(file, Files.readString(file, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("Could not parse " + file, e);
        }
    }

    public static Buildspec of(Path file, String document) {
        final ObjectMapper mapper = JsonMapper.builder()
                .build();
        final String mini = minify(document);
        final String json = parseToJson(mini);
        JsonNode f = mapper.readTree(json);
        Builder b = new Builder();

        for (JsonNode stmt : f.path("Stmts")) {
            JsonNode assigns = stmt.path("Cmd").path("Assigns");
            if (assigns.size() != 1) {
                throw new IllegalStateException("Assigns.size() != 1 for node " + assigns);
            }
            JsonNode assign = assigns.get(0);
            String k = assign.path("Name").path("Value").asString();
            JsonNode parts = assign.path("Value").path("Parts");
            String value = join(k, mini, parts);
            //log.info(key + "=" + value);
            b.entry(k, value);
        }
        return b.build(file);
    }

    static String join(String key, String src, JsonNode parts) {
        if (parts.size() == 1) {
            JsonNode part0 = parts.get(0);
            String type = part0.path("Type").asString();
            if (type.equals("Lit")) {
                return part0.path("Value").asString();
            } else if (type.equals("DblQuoted")) {
                int start = part0.path("Left").path("Offset").asInt() + 1;
                int end = part0.path("Right").path("Offset").asInt();
                return src.substring(start, end);
            } else if (type.equals("SglQuoted")) {
                int start = part0.path("Left").path("Offset").asInt() + 1;
                int end = part0.path("Right").path("Offset").asInt();
                return src.substring(start, end);
            } else if (type.equals("CmdSubst")) {
                int start = part0.path("Pos").path("Offset").asInt();
                int end = part0.path("End").path("Offset").asInt();
                return src.substring(start, end);
            } else if (type.equals("ParamExp")) {
                int start = part0.path("Pos").path("Offset").asInt();
                int end = part0.path("End").path("Offset").asInt();
                return src.substring(start, end);
            } else {
                throw new IllegalStateException("Unexpected type " + type + " for key " + key);
            }
        } else if (parts.size() == 0) {
            return null;
        } else {
            //log.info("Joining key " + key + ": " + parts.toPrettyString());
            JsonNode part0 = parts.get(0);
            int start = part0.path("Pos").path("Offset").asInt();
            int end = parts.get(parts.size() - 1).path("End").path("Offset").asInt();
            return src.substring(start, end);
        }
    }

    static String parseToJson(String file) {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        CliAssured
                .command("shfmt", "--to-json")
                .stdin(file)
                .then()
                .stdout()
                //.log(log::info)
                .redirect(baos)
                .execute()
                .assertSuccess();
        byte[] bytes = baos.toByteArray();
        return new String(bytes, StandardCharsets.UTF_8);
    }

    static String minify(String file) {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        CliAssured
                .command("shfmt", "--minify")
                .stdin(file)
                .then()
                .stdout()
                //.log(log::info)
                .redirect(baos)
                .execute()
                .assertSuccess();
        byte[] bytes = baos.toByteArray();
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static Builder builder() {
        return new Builder();
    }

    enum Key {
        groupId,
        artifactId,
        version,
        display,
        referenceRepo,
        layout,
        gitRepo,
        gitTag,
        sourceDistribution,
        sourcePath,
        sourceRmFiles,
        tool,
        jdk,
        toolchains,
        newline,
        newlineGit,
        umask,
        timezone,
        locale,
        os,
        arch,
        jdkForceAzul,
        workdir,
        command,
        execBefore,
        execAfter,
        buildinfo,
        diffoscope,
        issue;

        private static final Map<String, Key> KEYS = Stream.of(values())
                .map(key -> new AbstractMap.SimpleImmutableEntry<String, Key>(key.name(), key))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue));

        public static Key of(String rawKey) {
            return KEYS.get(rawKey);
        }
    }

    public static enum Newline {
        lf("lf"), crlf("crlf");

        private final String literal;

        private Newline(String literal) {
            this.literal = literal;
        }

        public String toString() {
            return literal;
        }

        public static Newline of(String literal, Path file) {
            return switch (literal) {
            case "11" -> lf; // see https://github.com/jvm-repo-rebuild/reproducible-central/pull/4555
            case "lf" -> lf;
            case "crlf" -> crlf;
            case "crlf,no-auto.crlf" -> report(file, literal, crlf); // TODO https://github.com/jvm-repo-rebuild/reproducible-central/issues/4556
            case "crlf-nogit" -> report(file, literal, crlf); // TODO https://github.com/jvm-repo-rebuild/reproducible-central/issues/4556
            default -> throw new IllegalArgumentException("Unexpected " + Newline.class.getName() + " value: " + literal);
            };
        }

        static Newline report(Path file, String literal, Newline crlf) {
            log.warn("Bad newline value " + literal + " in " + file);
            return crlf;
        }
    }

    public static class Builder {

        private Map<String, String> values = new LinkedHashMap<>();

        public void entry(String key, String value) {
            values.put(key, value);
        }

        public Builder() {
            values.put(Key.locale.name(), "en_US");
            values.put(Key.timezone.name(), "UTC");
            values.put(Key.umask.name(), "0002");
            values.put(Key.referenceRepo.name(), "https://repo.maven.apache.org/maven2/");
            values.put(Key.display.name(), "${groupId}:${artifactId}");
        }

        public Buildspec build(Path file) {

            final String groupId = resolveMandatory(Key.groupId);
            final String artifactId = resolveMandatory(Key.artifactId);
            final String version = resolveMandatory(Key.version);
            final String display = resolveMandatory(Key.display);
            final String referenceRepo = resolve(Key.referenceRepo);
            final String layout = resolve(Key.layout);
            final String sourceDistribution = resolve(Key.sourceDistribution);
            final String gitRepo;
            final String gitTag;
            if (sourceDistribution == null) {
                gitRepo = resolveMandatory(Key.gitRepo);
                gitTag = resolveMandatory(Key.gitTag);
            } else {
                gitRepo = null;
                gitTag = null;
            }
            final String sourcePath = resolve(Key.sourcePath);
            final String sourceRmFiles = resolve(Key.sourceRmFiles);
            final String tool = resolveMandatory(Key.tool);
            final String jdk = resolveMandatory(Key.jdk);
            final String toolchains = resolve(Key.toolchains);
            final Newline newline = Newline.of(resolveMandatory(Key.newline), file);
            final String rawNewlineGit = resolve(Key.newlineGit);
            final Newline newlineGit = rawNewlineGit == null ? null : Newline.of(rawNewlineGit, file);
            final String umask = resolve(Key.umask);
            final String timezone = resolve(Key.timezone);
            final String locale = resolve(Key.locale);
            final String os = resolve(Key.os);
            final String arch = resolve(Key.arch);
            final boolean jdkForceAzul = Boolean.parseBoolean(resolve(Key.jdkForceAzul));
            final String workdir = resolve(Key.workdir);
            final String command = resolveMandatory(Key.command);
            final String execBefore = resolve(Key.execBefore);
            final String execAfter = resolve(Key.execAfter);
            final String buildinfo = resolveMandatory(Key.buildinfo);
            final String diffoscope = resolve(Key.diffoscope);
            final String issue = resolve(Key.issue);

            return new Buildspec(groupId, artifactId, version, display, referenceRepo, layout, gitRepo, gitTag,
                    sourceDistribution,
                    sourcePath, sourceRmFiles, tool, jdk, toolchains, newline, newlineGit, umask, timezone, locale, os, arch,
                    jdkForceAzul,
                    workdir, command, execBefore, execAfter, buildinfo, diffoscope, issue);
        }

        private static final Pattern BRACED_EXPRESSION_PATTERN = Pattern.compile("\\$\\{([^\\}]+)\\}");
        private static final Pattern SIMPLE_EXPRESSION_PATTERN = Pattern.compile("\\$([a-zA-Z_][0-9a-zA-Z_]*)");

        private String resolveMandatory(Key key) {
            String result = resolve(key);
            if (result == null) {
                throw new IllegalStateException("Missing required key " + key);
            }
            return result;
        }

        String resolve(Key key) {
            return resolve(key.name());
        }

        String resolve(String key) {
            String val = values.get(key);
            if (val == null) {
                return null;
            }
            while (true) {
                final String newVal = eval(val);
                if (newVal.equals(val)) {
                    val = newVal;
                    break;
                }
                val = newVal;
            }
            values.put(key, val);
            return val;
        }

        private String eval(final String val) {
            CharSequence src = replace(BRACED_EXPRESSION_PATTERN, true, val);
            src = replace(SIMPLE_EXPRESSION_PATTERN, false, src);
            return src.toString();
        }

        private StringBuilder replace(Pattern pattern, boolean braced, CharSequence val) {
            StringBuilder sb = new StringBuilder();
            {
                Matcher m = pattern.matcher(val);
                while (m.find()) {
                    String k = m.group(1);
                    String v = values.get(k);
                    if (v == null) {
                        v = braced ? ("${" + k + "}") : ("$" + k);
                    }
                    m.appendReplacement(sb, v.replace("\\", "\\\\").replace("$", "\\$"));
                }
                m.appendTail(sb);
            }
            return sb;
        }

    }
}
