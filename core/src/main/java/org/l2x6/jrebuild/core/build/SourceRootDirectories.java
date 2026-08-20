/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.build;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

public class SourceRootDirectories {
    private SourceRootDirectories() {
    }

    public static Function<Path, List<Path>> root() {
        return path -> List.of(path);
    }

    public static Function<Path, List<Path>> roots(String root, String... moreRoots) {
        return path -> Stream.concat(Stream.of(root), Stream.of(moreRoots)).map(rt -> path.resolve(rt)).toList();
    }

    public static Function<Path, List<Path>> roots(Collection<Path> roots) {
        if (roots == null || roots.isEmpty()) {
            return root();
        }
        return path -> roots.stream().map(rt -> path.resolve(rt)).toList();
    }
}
