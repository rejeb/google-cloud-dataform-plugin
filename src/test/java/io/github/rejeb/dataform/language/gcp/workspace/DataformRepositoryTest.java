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
package io.github.rejeb.dataform.language.gcp.workspace;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataformRepositoryTest {

    private static DataformRepository withRemote(String url) {
        return new DataformRepository("r", url, "main", "");
    }

    @Test
    void aRepositoryHasARemoteOnlyWithAUrl() {
        assertTrue(withRemote("https://github.com/acme/core.git").hasGitRemote());
        assertFalse(withRemote(null).hasGitRemote());
        assertFalse(withRemote("  ").hasGitRemote());
    }

    @Test
    void anHttpsRemoteIsShownWithoutSchemeAndSuffix() {
        assertEquals("github.com/acme/core", withRemote("https://github.com/acme/core.git").remoteDisplayName());
    }

    @Test
    void credentialsInTheUrlAreNeverShown() {
        assertEquals("github.com/acme/core", withRemote("https://token@github.com/acme/core.git").remoteDisplayName());
    }

    @Test
    void anScpLikeRemoteIsShownAsAPath() {
        assertEquals("github.com/acme/core", withRemote("git@github.com:acme/core.git").remoteDisplayName());
    }

    @Test
    void anSshRemoteKeepsItsPort() {
        assertEquals("git.acme.io:2222/data/core", withRemote("ssh://git@git.acme.io:2222/data/core").remoteDisplayName());
    }

    @Test
    void noRemoteHasNoDisplayName() {
        assertNull(withRemote(null).remoteDisplayName());
    }

    @Test
    void aRemoteIsRecognizedWhateverItsProtocol() {
        DataformRepository repository = withRemote("https://github.com/acme/core.git");

        assertTrue(repository.isRemote("https://github.com/acme/core.git"));
        assertTrue(repository.isRemote("git@github.com:acme/core.git"));
        assertTrue(repository.isRemote("ssh://git@github.com/acme/core.git"));
        assertTrue(repository.isRemote("ssh://git@github.com:22/acme/core"));
    }

    @Test
    void aRemoteIsRecognizedWhateverItsCredentialsSuffixOrCase() {
        DataformRepository repository = withRemote("https://github.com/acme/core");

        assertTrue(repository.isRemote("https://user:token@github.com/acme/core.git"));
        assertTrue(repository.isRemote("https://github.com/acme/core/"));
        assertTrue(repository.isRemote("https://GitHub.com/Acme/Core"));
    }

    @Test
    void anotherRepositoryOrHostIsNotTheRemote() {
        DataformRepository repository = withRemote("https://github.com/acme/core.git");

        assertFalse(repository.isRemote("https://github.com/acme/other.git"));
        assertFalse(repository.isRemote("https://gitlab.com/acme/core.git"));
    }

    @Test
    void aRepositoryWithoutARemoteRecognizesNone() {
        assertFalse(withRemote(null).isRemote("https://github.com/acme/core.git"));
    }

    @Test
    void aWorkspacePushesToTheDefaultBranchOrItsOwn() {
        assertEquals(List.of("main", "dev"), withRemote("https://github.com/acme/core.git").pushBranches("dev"));
    }

    @Test
    void aWorkspaceNamedAfterTheDefaultBranchHasOneTarget() {
        assertEquals(List.of("main"), withRemote("https://github.com/acme/core.git").pushBranches("main"));
    }

    @Test
    void anUnknownDefaultBranchLeavesTheWorkspaceBranch() {
        assertEquals(List.of("dev"),
                new DataformRepository("r", "https://github.com/acme/core.git", null, "").pushBranches("dev"));
    }

    @Test
    void aRepositoryWithoutARemoteHasNoTarget() {
        assertEquals(List.of(), withRemote(null).pushBranches("dev"));
    }
}
