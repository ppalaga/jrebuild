/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.jackson;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.l2x6.pom.tuner.model.Ga;
import org.l2x6.pom.tuner.model.Gavtc;

public class Serializers {
    public static class GaSerializer extends StdSerializer<Ga> {
        GaSerializer() {
            super(Ga.class);
        }

        @Override
        public void serialize(Ga value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeString(value.toString());
        }
    }

    public static class GaDeserializer extends StdDeserializer<Ga> {
        GaDeserializer() {
            super(Ga.class);
        }

        @Override
        public Ga deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
            try {
                return Ga.of(p.getValueAsString());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

    }

    public static class GavtcSerializer extends StdSerializer<Gavtc> {
        private static final long serialVersionUID = 1L;

        GavtcSerializer() {
            super(Gavtc.class);
        }

        @Override
        public void serialize(Gavtc value, JsonGenerator gen, SerializerProvider provider) throws JacksonException {
            try {
                gen.writeString(value.toString());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    public static class GavtcDeserializer extends StdDeserializer<Gavtc> {
        private static final long serialVersionUID = 1L;

        GavtcDeserializer() {
            super(Gavtc.class);
        }

        @Override
        public Gavtc deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
            try {
                return Gavtc.of(p.getValueAsString());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

    }

    public static class GavtcKeySerializer extends StdSerializer<Gavtc> {
        private static final long serialVersionUID = 1L;

        GavtcKeySerializer() {
            super(Gavtc.class);
        }

        @Override
        public void serialize(Gavtc value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeFieldName(value.toString());
        }
    }

    public static class GavtcKeyDeserializer extends KeyDeserializer {

        @Override
        public Object deserializeKey(String key, DeserializationContext ctxt) throws IOException {
            return Gavtc.of(key);
        }

    }

}
