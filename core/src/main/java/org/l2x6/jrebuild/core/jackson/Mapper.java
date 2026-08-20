/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.jackson;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.l2x6.jrebuild.api.scm.FqScmRef;
import org.l2x6.jrebuild.api.scm.ScmRepository;

import static com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature.INDENT_ARRAYS_WITH_INDICATOR;
import static com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature.SPLIT_LINES;

public class Mapper {
    private static final ObjectMapper MAPPER = JsonMapper.builder(new YAMLFactory()
            .disable(SPLIT_LINES)
            .enable(INDENT_ARRAYS_WITH_INDICATOR)
            .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES))
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            // .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
            .addModule(new JavaTimeModule())
            .addModule(new SimpleModule()
                    .addAbstractTypeMapping(FqScmRef.class, FqScmRef.FqScmRefRecord.class)
                    .addAbstractTypeMapping(ScmRepository.class, ScmRepository.ScmRepositoryRecord.class))
            .build().setDefaultPropertyInclusion(JsonInclude.Include.NON_DEFAULT);

    public static ObjectMapper instance() {
        return MAPPER;
    }
}
