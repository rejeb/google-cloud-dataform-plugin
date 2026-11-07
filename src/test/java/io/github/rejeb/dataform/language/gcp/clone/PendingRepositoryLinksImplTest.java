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
package io.github.rejeb.dataform.language.gcp.clone;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PendingRepositoryLinksImplTest {

    private static final RepositoryLink LINK =
            new RepositoryLink("acme", "europe-west1", "analytics-core", "", null);

    private final PendingRepositoryLinks links = new PendingRepositoryLinksImpl();

    @Test
    void aLinkIsHandedOverToTheProjectOfItsDirectory() {
        links.register(Path.of("/work/analytics-core"), LINK);

        assertEquals(LINK, links.take("/work/analytics-core"));
    }

    @Test
    void aLinkIsHandedOverOnce() {
        links.register(Path.of("/work/analytics-core"), LINK);
        links.take("/work/analytics-core");

        assertNull(links.take("/work/analytics-core"));
    }

    @Test
    void anotherProjectGetsNothingAndLeavesTheLinkWaiting() {
        links.register(Path.of("/work/analytics-core"), LINK);

        assertNull(links.take("/work/other"));
        assertEquals(LINK, links.take("/work/analytics-core"));
    }

    @Test
    void differentSpellingsOfTheSameDirectoryMatch() {
        links.register(Path.of("/work/tmp/../analytics-core/"), LINK);

        assertEquals(LINK, links.take("/work/analytics-core"));
    }
}
