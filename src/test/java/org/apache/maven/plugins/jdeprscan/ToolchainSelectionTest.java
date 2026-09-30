/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.maven.plugins.jdeprscan;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.maven.api.Session;
import org.apache.maven.api.Toolchain;
import org.apache.maven.api.services.ToolchainManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ToolchainSelectionTest {

    private static final Session SESSION = null;

    private static Toolchain toolchain() {
        return (Toolchain) Proxy.newProxyInstance(
                ToolchainSelectionTest.class.getClassLoader(), new Class<?>[] {Toolchain.class}, (p, m, a) -> null);
    }

    private static ToolchainManager manager(Optional<Toolchain> fromBuildContext, List<Toolchain> matching) {
        return (ToolchainManager) Proxy.newProxyInstance(
                ToolchainSelectionTest.class.getClassLoader(), new Class<?>[] {ToolchainManager.class}, (p, m, a) -> {
                    switch (m.getName()) {
                        case "getToolchainFromBuildContext":
                            return fromBuildContext;
                        case "getToolchains":
                            assertEquals(Map.of("version", "[9,)"), a[2]);
                            return matching;
                        default:
                            throw new UnsupportedOperationException(m.getName());
                    }
                });
    }

    @Test
    void toolchainFromBuildContextWins() {
        Toolchain selected = toolchain();
        ToolchainManager manager = manager(Optional.of(selected), List.of(toolchain()));

        assertSame(selected, AbstractJDeprScanMojo.selectToolchain(manager, SESSION));
    }

    @Test
    void newestMatchingToolchainIsUsedWithoutBuildContext() {
        Toolchain newest = toolchain();
        ToolchainManager manager = manager(Optional.empty(), List.of(toolchain(), newest));

        assertSame(newest, AbstractJDeprScanMojo.selectToolchain(manager, SESSION));
    }

    @Test
    void noToolchainWhenNothingMatches() {
        assertNull(AbstractJDeprScanMojo.selectToolchain(manager(Optional.empty(), List.of()), SESSION));
    }
}
