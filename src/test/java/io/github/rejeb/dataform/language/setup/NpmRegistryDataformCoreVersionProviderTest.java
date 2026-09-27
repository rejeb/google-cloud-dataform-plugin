/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.rejeb.dataform.language.setup;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class NpmRegistryDataformCoreVersionProviderTest {

    @Test
    public void readsTheVersionOfARegistryManifest() {
        assertEquals("3.0.57", NpmRegistryDataformCoreVersionProvider.parseVersion(
                "{\"name\":\"@dataform/core\",\"version\":\"3.0.57\",\"main\":\"bundle.js\"}"));
    }

    @Test
    public void ignoresManifestsWithoutAUsableVersion() {
        assertNull(NpmRegistryDataformCoreVersionProvider.parseVersion(null));
        assertNull(NpmRegistryDataformCoreVersionProvider.parseVersion(""));
        assertNull(NpmRegistryDataformCoreVersionProvider.parseVersion("not json"));
        assertNull(NpmRegistryDataformCoreVersionProvider.parseVersion("[]"));
        assertNull(NpmRegistryDataformCoreVersionProvider.parseVersion("{\"name\":\"@dataform/core\"}"));
        assertNull(NpmRegistryDataformCoreVersionProvider.parseVersion("{\"version\":{}}"));
        assertNull(NpmRegistryDataformCoreVersionProvider.parseVersion("{\"version\":\" \"}"));
    }

    @Test
    public void proposesTheFallbackUntilAVersionIsFetched() {
        assertEquals(DataformCoreVersionProvider.FALLBACK_VERSION,
                new NpmRegistryDataformCoreVersionProvider().knownLatestVersion());
    }

    @Test
    public void readsTheCliVersionInstalledBesideTheCore(@TempDir Path dataformDir) throws Exception {
        Path core = Files.createDirectories(dataformDir.resolve("core"));
        Path cli = Files.createDirectories(dataformDir.resolve("cli"));
        Files.writeString(cli.resolve("package.json"), "{\"name\":\"@dataform/cli\",\"version\":\"3.0.46\"}");

        assertEquals(Optional.of("3.0.46"), NpmRegistryDataformCoreVersionProvider.cliVersionNextTo(core));
    }

    @Test
    public void noCliBesideTheCoreGivesNoVersion(@TempDir Path dataformDir) throws Exception {
        Path core = Files.createDirectories(dataformDir.resolve("core"));

        assertEquals(Optional.empty(), NpmRegistryDataformCoreVersionProvider.cliVersionNextTo(core));
    }
}
