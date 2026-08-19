/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild
 *                                 project contributors as indicated by the @author tags
 *                                 SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.pnc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import org.jboss.pnc.dto.response.ArtifactInfo;
import org.l2x6.jrebuild.common.io.IoUtils;

public record CacheEntry(String rawKey, Path filePath) {

    public static CacheEntry of(Path cacheDir, String rawKey) {
        return new CacheEntry(
                rawKey,
                cacheDir.resolve(
                        IoUtils.sanitizeFileName(
                                rawKey
                                        .replace('*', '%')
                                        .replace(':', '$'))
                                + ".json"));
    }

    public <T> Optional<T> read(ObjectMapper mapper, Predicate<Path> isExpired, TypeReference<T> type) {
        if (Files.isRegularFile(filePath)) {
            if (isExpired.test(filePath)) {
                return Optional.empty();
            }
            try {
                return Optional.of(mapper.readValue(Files.readAllBytes(filePath), type));
            } catch (IOException e) {
                throw new UncheckedIOException("Could not read " + filePath, e);
            }
        }
        return Optional.empty();
    }

    public void write(ObjectMapper mapper, List<ArtifactInfo> result) {
        try (OutputStream out = Files.newOutputStream(filePath)) {
            mapper.writeValue(out, result);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write to " + filePath, e);
        }
    }

    public void write(byte[] bytes) {
        try {
            Files.write(filePath, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write to " + filePath, e);
        }
    }

}
