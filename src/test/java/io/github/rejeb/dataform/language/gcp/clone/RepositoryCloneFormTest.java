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

import io.github.rejeb.dataform.language.gcp.workspace.DataformRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepositoryCloneFormTest {

    private static final DataformRepository WITH_REMOTE = new DataformRepository(
            "analytics-core", "https://github.com/acme/analytics-core.git", "main", "");
    private static final DataformRepository WITHOUT_REMOTE = new DataformRepository(
            "finance-marts", null, null, "");

    private final RepositoryCloneForm form = new RepositoryCloneForm();

    @Test
    void nothingCanBeFetchedBeforeARepositoryIsChosen() {
        assertFalse(form.canProceed());
        assertEquals("Clone", form.actionName());
    }

    @Test
    void aRepositoryWithARemoteDefaultsToCloningIt() {
        form.selectRepository(WITH_REMOTE);

        assertEquals(RepositorySource.GIT_REMOTE, form.source());
        assertEquals("Clone", form.actionName());
        assertTrue(form.canProceed());
        assertFalse(form.needsWorkspaces());
    }

    @Test
    void aRepositoryWithoutARemoteCanOnlyBeDownloadedFromAWorkspace() {
        form.selectRepository(WITHOUT_REMOTE);

        form.selectSource(RepositorySource.GIT_REMOTE);

        assertEquals(RepositorySource.WORKSPACE, form.source());
        assertEquals("Download", form.actionName());
        assertTrue(form.needsWorkspaces());
    }

    @Test
    void aWorkspaceDownloadWaitsForTheWorkspaces() {
        form.selectRepository(WITH_REMOTE);

        form.selectSource(RepositorySource.WORKSPACE);

        assertFalse(form.canProceed());
        assertTrue(form.needsWorkspaces());
    }

    @Test
    void workspacesAreRequestedOnce() {
        form.selectRepository(WITHOUT_REMOTE);

        form.workspacesRequested();

        assertFalse(form.needsWorkspaces());
        assertFalse(form.canProceed());
    }

    @Test
    void theFirstLoadedWorkspaceIsSelected() {
        form.selectRepository(WITHOUT_REMOTE);

        form.workspacesLoaded(List.of("dev-you", "staging"));

        assertEquals("dev-you", form.selectedWorkspace());
        assertEquals(List.of("dev-you", "staging"), form.workspaces());
        assertTrue(form.canProceed());
        assertNull(form.workspaceProblem());
    }

    @Test
    void anotherWorkspaceCanBeSelected() {
        form.selectRepository(WITHOUT_REMOTE);
        form.workspacesLoaded(List.of("dev-you", "staging"));

        form.selectWorkspace("staging");

        assertEquals("staging", form.selectedWorkspace());
    }

    @Test
    void aRepositoryWithoutWorkspacesCannotBeDownloaded() {
        form.selectRepository(WITHOUT_REMOTE);

        form.workspacesLoaded(List.of());

        assertFalse(form.canProceed());
        assertNotNull(form.workspaceProblem());
    }

    @Test
    void aFailedWorkspaceListingIsReportedAndBlocksTheDownload() {
        form.selectRepository(WITHOUT_REMOTE);

        form.workspacesFailed("Permission denied");

        assertFalse(form.canProceed());
        assertEquals("Permission denied", form.workspaceProblem());
    }

    @Test
    void aWorkspaceProblemDoesNotBlockCloningTheRemote() {
        form.selectRepository(WITH_REMOTE);
        form.selectSource(RepositorySource.WORKSPACE);
        form.workspacesFailed("Permission denied");

        form.selectSource(RepositorySource.GIT_REMOTE);

        assertTrue(form.canProceed());
        assertEquals("Clone", form.actionName());
    }

    @Test
    void choosingAnotherRepositoryGoesBackToItsDefaultSourceAndForgetsTheWorkspaces() {
        form.selectRepository(WITHOUT_REMOTE);
        form.workspacesLoaded(List.of("dev-you"));

        form.selectRepository(WITH_REMOTE);

        assertEquals(RepositorySource.GIT_REMOTE, form.source());
        assertNull(form.selectedWorkspace());
        assertEquals(List.of(), form.workspaces());
    }

    @Test
    void clearingTheRepositoryStopsTheFetch() {
        form.selectRepository(WITH_REMOTE);

        form.selectRepository(null);

        assertFalse(form.canProceed());
        assertNull(form.repository());
    }
}
