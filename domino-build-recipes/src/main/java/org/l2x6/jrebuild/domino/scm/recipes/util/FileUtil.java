/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.domino.scm.recipes.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class FileUtil {
    public static void deleteRecursive(final java.nio.file.Path file) {
        try {
            if (Files.isDirectory(file)) {
                try (Stream<Path> files = Files.list(file)) {
                    files.forEach(FileUtil::deleteRecursive);
                }
            }
            Files.delete(file);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
