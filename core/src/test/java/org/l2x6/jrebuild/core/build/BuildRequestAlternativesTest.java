/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.build;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.StreamSupport;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.l2x6.jrebuild.core.jackson.Mapper;

public class BuildRequestAlternativesTest {
    @Test
    void iterator() throws IOException {
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("BuildRequestAlternativesTest/BuildRequestAlternatives.yaml")) {
            BuildRequestAlternatives alts = Mapper.instance().readValue(in, BuildRequestAlternatives.class);
            List<BuildRequest> requests = StreamSupport.stream(alts.spliterator(), false).toList();
            Path out = Path.of("target/BuildRequestAlternativesTest/BuildRequest.yaml");
            Files.createDirectories(out.getParent());
            Mapper.instance().writeValue(out.toFile(), requests);
            Assertions.assertThat(out).hasContent("foo");
        }
    }
}
