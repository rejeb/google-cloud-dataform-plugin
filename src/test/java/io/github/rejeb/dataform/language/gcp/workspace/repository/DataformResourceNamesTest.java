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
package io.github.rejeb.dataform.language.gcp.workspace.repository;

import com.google.api.gax.rpc.FailedPreconditionException;
import com.google.api.gax.rpc.StatusCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

public class DataformResourceNamesTest {

    @Test
    public void buildsTheRepositoryResourceName() {
        assertEquals("projects/p/locations/europe-west1/repositories/r",
                DataformResourceNames.repositoryName("p", "europe-west1", "r"));
    }

    @Test
    public void buildsTheWorkspaceResourceName() {
        assertEquals("projects/p/locations/europe-west1/repositories/r/workspaces/w",
                DataformResourceNames.workspaceName("p", "europe-west1", "r", "w"));
    }

    @Test
    public void emptyRepositoryIsRecognisedByMessage() {
        assertTrue(DataformResourceNames.isEmptyRepoException(
                new RuntimeException("Reading from empty repo is not allowed")));
        assertTrue(DataformResourceNames.isEmptyRepoException(
                new RuntimeException("wrapper", new IllegalStateException("Reading from empty repo"))));
    }

    @Test
    public void emptyRepositoryIsRecognisedByFailedPrecondition() {
        assertTrue(DataformResourceNames.isEmptyRepoException(new RuntimeException("wrapper",
                new FailedPreconditionException("precondition", null, mock(StatusCode.class), false))));
    }

    @Test
    public void otherFailuresAreNotAnEmptyRepository() {
        assertFalse(DataformResourceNames.isEmptyRepoException(new RuntimeException("not found")));
        assertFalse(DataformResourceNames.isEmptyRepoException(new RuntimeException((String) null)));
    }
}
