/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild
 *                                 project contributors as indicated by the @author tags
 *                                 SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.common.io;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class IoUtilsTest {

    @Test
    void sanitizeFileName() {
        Assertions.assertThat(IoUtils.sanitizeFileName("foo")).isEqualTo("foo");
        Assertions.assertThat(IoUtils.sanitizeFileName("foo\\")).isEqualTo("foo");
        for (char ch : IoUtils.FORBIDDEN_CHARS.toCharArray()) {
            Assertions.assertThat(IoUtils.sanitizeFileName("foo" + ch)).isEqualTo("foo");
        }
    }
}
