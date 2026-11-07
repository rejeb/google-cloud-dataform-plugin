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
package io.github.rejeb.dataform.language.gcp.toolwindow;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.gcp.workspace.UncommittedChange;
import io.github.rejeb.dataform.language.gcp.workspace.UncommittedChange.ChangeState;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DataformRepoTreeModelTest extends BasePlatformTestCase {

    public void testDeletedFilesOfTheWorkspaceAreListedWithTheOthers() {
        DataformRepoTreeModel model = new DataformRepoTreeModel("sales");
        model.setFiles(List.of("definitions/a.sqlx", "workflow_settings.yaml"));

        model.setGitStatuses(List.of(new UncommittedChange("definitions/old.sqlx", ChangeState.DELETED)));

        assertEquals(List.of("definitions/a.sqlx", "definitions/old.sqlx", "workflow_settings.yaml"), filePaths(model));
    }

    public void testDeletedFilesStayListedWhenTheFilesArriveLast() {
        DataformRepoTreeModel model = new DataformRepoTreeModel("sales");
        model.setGitStatuses(List.of(new UncommittedChange("definitions/old.sqlx", ChangeState.DELETED)));

        model.setFiles(List.of("definitions/a.sqlx"));

        assertEquals(List.of("definitions/a.sqlx", "definitions/old.sqlx"), filePaths(model));
    }

    public void testADirectoryKnowsWhetherItHoldsChanges() {
        DataformRepoTreeModel model = new DataformRepoTreeModel("sales");
        model.setFiles(List.of("definitions/staging/a.sqlx", "includes/b.js"));

        model.setGitStatuses(List.of(new UncommittedChange("definitions/staging/a.sqlx", ChangeState.MODIFIED)));

        assertTrue(model.hasChangesUnder("definitions"));
        assertTrue(model.hasChangesUnder("definitions/staging"));
        assertFalse(model.hasChangesUnder("includes"));
        assertFalse(model.hasChangesUnder("definitions/stag"));
    }

    private static List<String> filePaths(DataformRepoTreeModel model) {
        List<String> paths = new ArrayList<>();
        for (DefaultMutableTreeNode node : Collections.list(((DefaultMutableTreeNode) model.getRoot()).depthFirstEnumeration())
                .stream().map(DefaultMutableTreeNode.class::cast).toList()) {
            DataformRepoTreeModel.FileEntry entry = model.getFileEntry(node);
            if (entry != null) {
                paths.add(entry.relativePath());
            }
        }
        Collections.sort(paths);
        return paths;
    }
}
