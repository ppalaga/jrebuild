/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild
 *                                 project contributors as indicated by the @author tags
 *                                 SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.pnc;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientResponseContext;
import jakarta.ws.rs.client.ClientResponseFilter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.function.Function;
import java.util.function.Predicate;

public record CachingResponseFilter(
        Predicate<URI> isCached,
        Function<URI, CacheEntry> getFile) implements ClientResponseFilter {

    @Override
    public void filter(ClientRequestContext requestContext, ClientResponseContext responseContext) throws IOException {
        final URI uri = requestContext.getUri();
        if (isCached.test(uri) && responseContext.hasEntity()) {
            try (InputStream is = responseContext.getEntityStream()) {
                byte[] bytes = is.readAllBytes();
                getFile.apply(uri).write(bytes);
                responseContext.setEntityStream(new ByteArrayInputStream(bytes));
            } catch (IOException e) {
                throw new UncheckedIOException("Could not read from entity stream of " + uri, e);
            }
        }
    }

}
