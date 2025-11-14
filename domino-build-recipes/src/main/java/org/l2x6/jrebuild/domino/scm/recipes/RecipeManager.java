/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.domino.scm.recipes;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature.INDENT_ARRAYS_WITH_INDICATOR;
import static com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature.MINIMIZE_QUOTES;
import static com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature.SPLIT_LINES;

public interface RecipeManager<T> {

    ObjectMapper MAPPER = JsonMapper.builder(new YAMLFactory()
            .disable(SPLIT_LINES)
            .disable(MINIMIZE_QUOTES)
            .enable(INDENT_ARRAYS_WITH_INDICATOR))
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
            .build().setSerializationInclusion(JsonInclude.Include.NON_DEFAULT);

    default T parse(Path file) throws IOException {
        try (var in = Files.newInputStream(file)) {
            return parse(in);
        }
    }

    T parse(InputStream in) throws IOException;

    void write(T data, OutputStream out) throws IOException;

    default void write(T data, Path file) throws IOException {
        try (var out = Files.newOutputStream(file)) {
            write(data, out);
        }
    }
}
