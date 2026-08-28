/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Properties;
import org.jboss.logging.Logger;
import picocli.CommandLine;

public class CacheOptions {
    private static final Logger log = Logger.getLogger(CacheOptions.class);

    @CommandLine.Option(names = {
            "--cache-dir" },
            description = """
                    A directory under which various cache and index files are stored, such as ls-remotes-cache.txt - the tag name -> sha1 mappings are cached for remote SCM repositories.
                    """,
            defaultValue = "~/.cache/jrebuild")
    private Path cacheDir;
    private volatile Path cacheDirResolved;
    private volatile Path userHome;

    protected Path cacheDir() {
        Path result;
        if ((result = cacheDirResolved) == null) {
            result = cacheDirResolved = resolveHome(cacheDir);
        }
        return result;
    }

    public Path toolsCacheDir() {
        return cacheDir.resolve("tools");
    }

    public Path localReferenceMavenRepositoryDir() {
        return cacheDir.resolve("m2-ref");
    }

    public Path clonesDir() {
        return cacheDir.resolve("clones");
    }

    protected Path userHome() {
        Path result;
        if ((result = userHome) == null) {
            result = userHome = Path.of(System.getProperty("user.home"));
        }
        return result;
    }

    protected Path resolveHome(Path path) {
        if (path != null && path.startsWith("~")) {
            return userHome().resolve(path.subpath(1, path.getNameCount()));
        }
        return path;
    }

    protected static Instant defaultMinRetrievalTime(String key, Path cacheDir) {
        final Path lastRunProperties = cacheDir.resolve("last-run.properties");
        final Properties props = new Properties();
        boolean exists = Files.exists(lastRunProperties);
        if (exists) {
            try (InputStream in = Files.newInputStream(lastRunProperties)) {
                props.load(in);
            } catch (IOException e) {
                throw new RuntimeException("Could not read " + lastRunProperties);
            }
        }
        final String rawMinimalRetievalTime = (String) props.getProperty(key);
        if (rawMinimalRetievalTime != null) {
            final Instant result = Instant.parse(rawMinimalRetievalTime);
            Instant startOfTheDay = LocalDate.now()
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant();
            if (result.isBefore(startOfTheDay)) {
                return storeLastRun(lastRunProperties, props, key);
            } else {
                log.infof(
                        "Loaded default %s = %s from %s; if you may want to override the value using the option --refresh-remotes-older-than",
                        key,
                        result,
                        lastRunProperties);
                return result;
            }
        }
        return storeLastRun(lastRunProperties, props, key);
    }

    static Instant storeLastRun(Path lastRunProperties, Properties props, String key) {
        final Instant result = Instant.now();
        props.setProperty(key, result.toString());
        if (!Files.exists(lastRunProperties.getParent())) {
            try {
                Files.createDirectories(lastRunProperties.getParent());
            } catch (IOException e) {
                throw new RuntimeException("Could not create " + lastRunProperties.getParent());
            }
        }
        try (OutputStream out = Files.newOutputStream(lastRunProperties)) {
            props.store(out, "");
        } catch (IOException e) {
            throw new RuntimeException("Could not write " + lastRunProperties);
        }
        log.infof(
                "Setting refresh-remotes-older-than = %s for today and storing it in %s; if you may want to override the value using the option --refresh-remotes-older-than",
                result, lastRunProperties);
        return result;
    }
}
