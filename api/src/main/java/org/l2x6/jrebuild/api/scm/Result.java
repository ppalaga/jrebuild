/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild
 *                                 project contributors as indicated by the @author tags
 *                                 SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.api.scm;

import java.util.function.Function;

public record Result<R, E>(R result, E failure) {
    public Result(R result, E failure) {
        if (failure != null && result != null) {
            throw new IllegalStateException("Cannot set both result and failure");
        }
        this.result = result;
        this.failure = failure;
    }

    public boolean isFailure() {
        return failure != null;
    }

    public <U> Result<U, E> mapResult(Function<R, U> mapper) {
        if (failure == null) {
            return new Result<U, E>(mapper.apply(result), null);
        }
        return new Result<U, E>((U) null, failure);
    }

    public <U, V> Result<U, V> map(Function<R, U> resultMapper, Function<E, V> failureMapper) {
        if (failure == null) {
            return new Result<U, V>(resultMapper.apply(result), null);
        }
        return new Result<U, V>(null, failureMapper.apply(failure));
    }

    public <U> U reduce(Function<R, U> resultMapper, Function<E, U> failureMapper) {
        if (failure == null) {
            return resultMapper.apply(result);
        }
        return failureMapper.apply(failure);
    }

    public static <R, E> Result<R, E> success(R result) {
        return new Result<>(result, null);
    }

    public static <R, E> Result<R, E> failure(E failure) {
        return new Result<>(null, failure);
    }

    Result<R, E> verify(Function<Result<R, E>, Result<R, E>> verifier) {
        if (failure != null) {
            return this;
        }
        return verifier.apply(this);
    }
}
