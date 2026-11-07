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

import com.intellij.openapi.vcs.FileStatus;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.SimpleTextAttributes;
import io.github.rejeb.dataform.language.gcp.workspace.UncommittedChange.ChangeState;

public class WorkspaceFileStatusesTest extends BasePlatformTestCase {

    public void testEveryChangeStateHasItsVcsStatus() {
        assertSame(FileStatus.ADDED, WorkspaceFileStatuses.of(ChangeState.ADDED));
        assertSame(FileStatus.MODIFIED, WorkspaceFileStatuses.of(ChangeState.MODIFIED));
        assertSame(FileStatus.DELETED, WorkspaceFileStatuses.of(ChangeState.DELETED));
        assertSame(FileStatus.MERGED_WITH_CONFLICTS, WorkspaceFileStatuses.of(ChangeState.HAS_CONFLICTS));
        assertSame(FileStatus.NOT_CHANGED, WorkspaceFileStatuses.of(ChangeState.UNKNOWN));
        assertSame(FileStatus.NOT_CHANGED, WorkspaceFileStatuses.of(null));
    }

    public void testTheTextTakesTheStatusColour() {
        SimpleTextAttributes added = WorkspaceFileStatuses.attributes(ChangeState.ADDED);

        assertEquals(FileStatus.ADDED.getColor(), added.getFgColor());
        assertEquals(SimpleTextAttributes.STYLE_PLAIN, added.getStyle());
        assertSame(SimpleTextAttributes.REGULAR_ATTRIBUTES, WorkspaceFileStatuses.attributes(null));
    }

    public void testDeletedFilesAreStruckThrough() {
        SimpleTextAttributes deleted = WorkspaceFileStatuses.attributes(ChangeState.DELETED);

        assertEquals(FileStatus.DELETED.getColor(), deleted.getFgColor());
        assertTrue(deleted.isStrikeout());
    }

    public void testTheTooltipNamesTheStateInTheWorkspace() {
        assertEquals("Modified in the workspace", WorkspaceFileStatuses.tooltip(ChangeState.MODIFIED));
        assertEquals("Added in the workspace", WorkspaceFileStatuses.tooltip(ChangeState.ADDED));
        assertEquals("Deleted in the workspace", WorkspaceFileStatuses.tooltip(ChangeState.DELETED));
        assertNull(WorkspaceFileStatuses.tooltip(null));
    }
}
