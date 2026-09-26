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
package io.github.rejeb.dataform.language.gcp.settings;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class RepositoryConfigStateTest {

    @Test
    public void constructorFillsEveryFieldButTheSelectedWorkspace() {
        RepositoryConfigState state = new RepositoryConfigState("id", "Prod", "p", "r", "eu", "sa@p.iam");
        assertEquals("id", state.getRepositoryConfigId());
        assertEquals("Prod", state.getLabel());
        assertEquals("p", state.getProjectId());
        assertEquals("r", state.getRepositoryId());
        assertEquals("eu", state.getLocation());
        assertEquals("sa@p.iam", state.getServiceAccount());
        assertNull(state.getSelectedWorkspaceId());
    }

    @Test
    public void identityIsTheConfigIdOnly() {
        RepositoryConfigState a = new RepositoryConfigState("id", "A", "p1", "r1", "eu", null);
        RepositoryConfigState b = new RepositoryConfigState("id", "B", "p2", "r2", "us", "sa");
        RepositoryConfigState c = new RepositoryConfigState("other", "A", "p1", "r1", "eu", null);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
        assertNotEquals(a, null);
        assertNotEquals(a, "id");

        Set<RepositoryConfigState> set = new HashSet<>();
        set.add(a);
        set.add(b);
        set.add(c);
        assertEquals(2, set.size());
    }

    @Test
    public void defaultStateIsEmptyAndEqualToAnotherEmptyState() {
        RepositoryConfigState empty = new RepositoryConfigState();
        assertNull(empty.getRepositoryConfigId());
        assertEquals(empty, new RepositoryConfigState());
        empty.setSelectedWorkspaceId("ws");
        assertEquals("ws", empty.getSelectedWorkspaceId());
    }
}
