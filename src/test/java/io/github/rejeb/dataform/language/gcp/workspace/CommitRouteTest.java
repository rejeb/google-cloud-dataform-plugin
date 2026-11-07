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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommitRouteTest {

    private static final DataformRepository LINKED =
            new DataformRepository("sales", "https://example.com/sales.git", "main", "");
    private static final DataformRepository HOSTED = new DataformRepository("sales", null, null, "");

    @Test
    void aRepositoryWithoutRemoteCommitsThroughItsWorkspaceAndSaysNothing() {
        CommitRoute route = new CommitRoute(HOSTED, CommitRoute.LocalClone.NONE);

        assertTrue(route.commitsThroughWorkspace());
        assertFalse(route.suggestsCloning());
        assertFalse(route.handsOverToGit());
    }

    @Test
    void aRemoteWithoutCloneCommitsThroughItsWorkspaceAndSuggestsCloning() {
        CommitRoute route = new CommitRoute(LINKED, CommitRoute.LocalClone.NONE);

        assertTrue(route.commitsThroughWorkspace());
        assertTrue(route.suggestsCloning());
        assertFalse(route.handsOverToGit());
    }

    @Test
    void aCloneWithTheGitPluginCommitsWithGit() {
        CommitRoute route = new CommitRoute(LINKED, CommitRoute.LocalClone.WITH_GIT_PLUGIN);

        assertFalse(route.commitsThroughWorkspace());
        assertFalse(route.suggestsCloning());
        assertTrue(route.handsOverToGit());
    }

    @Test
    void aCloneWithoutTheGitPluginCommitsWithGitAndSaysNothing() {
        CommitRoute route = new CommitRoute(LINKED, CommitRoute.LocalClone.WITHOUT_GIT_PLUGIN);

        assertFalse(route.commitsThroughWorkspace());
        assertFalse(route.suggestsCloning());
        assertFalse(route.handsOverToGit());
    }
}
