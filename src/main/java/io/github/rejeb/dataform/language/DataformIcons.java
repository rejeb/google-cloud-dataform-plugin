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
package io.github.rejeb.dataform.language;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

public class DataformIcons {
    public static final Icon FILE = IconLoader.getIcon(
            "/icons/sqlxFile.svg",
            DataformIcons.class
    );

    /** Dataform lineage: two source tables feeding a target table, in the site colours of the SQLX file icon. */
    public static final Icon LINEAGE = IconLoader.getIcon(
            "/icons/lineage.svg",
            DataformIcons.class
    );

    /** Greyed out file icon, used for actions declaring {@code disabled: true}. */
    public static final Icon FILE_DISABLED = IconLoader.getIcon(
            "/icons/sqlxFileDisabled.svg",
            DataformIcons.class
    );

    /** Dataform unit test file: the SQLX mark with a flask in place of its chevron. */
    public static final Icon TEST_FILE = IconLoader.getIcon(
            "/icons/sqlxTestFile.svg",
            DataformIcons.class
    );

    /** Dataform Tools tool window icon: the prompt on a dark screen in light themes, without its screen in dark themes. */
    public static final Icon TOOL_WINDOW = IconLoader.getIcon(
            "/icons/toolWindowDataform.svg",
            DataformIcons.class
    );

    /** SQLX editor toolbar: show only the editor. */
    public static final Icon LAYOUT_EDITOR_ONLY = AllIcons.General.LayoutEditorOnly;

    /** SQLX editor toolbar: show the editor and the preview side by side. */
    public static final Icon LAYOUT_EDITOR_AND_PREVIEW = AllIcons.General.LayoutEditorPreview;

    /** SQLX editor toolbar: show only the preview. */
    public static final Icon LAYOUT_PREVIEW_ONLY = AllIcons.General.LayoutPreviewOnly;

    /** SQLX preview: the compiled queries. */
    public static final Icon PREVIEW_QUERY = IconLoader.getIcon(
            "/icons/previewQuery.svg",
            DataformIcons.class
    );

    /** SQLX preview: the schemas of the actions. */
    public static final Icon PREVIEW_SCHEMA = IconLoader.getIcon(
            "/icons/previewSchema.svg",
            DataformIcons.class
    );

    /** SQLX preview: the compiled unit test. */
    public static final Icon PREVIEW_TEST = IconLoader.getIcon(
            "/icons/previewTest.svg",
            DataformIcons.class
    );

    /** SQLX editor toolbar: compile the Dataform project. */
    public static final Icon COMPILE = AllIcons.Actions.Compile;

    /** Services view: the root of the queries run from the SQLX preview. */
    public static final Icon QUERIES = IconLoader.getIcon(
            "/icons/queries.svg",
            DataformIcons.class
    );

    /** SQLX editor toolbar: run the query, the action or the test of the file. */
    public static final Icon RUN = AllIcons.Actions.Execute;

    /** Status of a run: succeeded. */
    public static final Icon STATUS_SUCCEEDED = AllIcons.RunConfigurations.TestPassed;

    /** Status of a run: failed. */
    public static final Icon STATUS_FAILED = AllIcons.RunConfigurations.TestError;

    /** Status of a run: cancelled. */
    public static final Icon STATUS_CANCELLED = AllIcons.RunConfigurations.TestTerminated;

    /** Status of a workflow action: skipped or disabled. */
    public static final Icon STATUS_SKIPPED = AllIcons.RunConfigurations.TestSkipped;

    /** Status of a workflow action: waiting to run. */
    public static final Icon STATUS_PENDING = AllIcons.RunConfigurations.TestNotRan;

    /** Run console toolbar: Show Failed Only. */
    public static final Icon FAILED_ONLY = IconLoader.getIcon(
            "/icons/failedOnly.svg",
            DataformIcons.class
    );

    /** Stops a running query or workflow. */
    public static final Icon STOP = AllIcons.Actions.Suspend;

    /** Removes finished items from a list. */
    public static final Icon TRASH = AllIcons.Actions.GC;

    /** Exports a result. */
    public static final Icon DOWNLOAD = AllIcons.Actions.Download;

    /** Results pager: the first page. */
    public static final Icon PAGE_FIRST = AllIcons.Actions.Play_first;

    /** Results pager: the previous page. */
    public static final Icon PAGE_PREVIOUS = AllIcons.Actions.Play_back;

    /** Results pager: the next page. */
    public static final Icon PAGE_NEXT = AllIcons.Actions.Play_forward;

    /** Results pager: the last page. */
    public static final Icon PAGE_LAST = AllIcons.Actions.Play_last;

    /** Query preview: a table, a view or an incremental table. */
    public static final Icon ACTION_TABLE = AllIcons.Nodes.DataTables;

    /** Query preview: an assertion, the grey shield. */
    public static final Icon ACTION_ASSERTION = IconLoader.getIcon(
            "/icons/actionAssertion.svg",
            DataformIcons.class
    );

    /** Query preview: operations, the grey database. */
    public static final Icon ACTION_OPERATIONS = IconLoader.getIcon(
            "/icons/actionOperations.svg",
            DataformIcons.class
    );

    /** Schema preview: a declaration, the grey table. */
    public static final Icon ACTION_DECLARATION = IconLoader.getIcon(
            "/icons/actionDeclaration.svg",
            DataformIcons.class
    );

    /** Schema preview: a column. */
    public static final Icon SCHEMA_COLUMN = AllIcons.Nodes.DataColumn;

    /** Schema preview: a RECORD column, holding fields. */
    public static final Icon SCHEMA_RECORD = IconLoader.getIcon(
            "/icons/schemaRecord.svg",
            DataformIcons.class
    );

    /** Schema preview: Refresh schema. */
    public static final Icon REFRESH = AllIcons.Actions.Refresh;

    /** Query preview: Copy SQL. */
    public static final Icon COPY = AllIcons.Actions.Copy;

    /** Query preview: Open in BigQuery Console. */
    public static final Icon EXTERNAL_LINK = AllIcons.Ide.External_link_arrow;

    /** Dataform Tools: a Dataform repository. */
    public static final Icon REPOSITORY = IconLoader.getIcon(
            "/icons/repository.svg",
            DataformIcons.class
    );

    /** Dataform Tools: a workspace of a repository. */
    public static final Icon BRANCH = AllIcons.Vcs.Branch;

    /** Dataform Tools: copy the workspace files into the project. */
    public static final Icon PULL = AllIcons.Actions.CheckOut;

    /** Dataform Tools: copy the project files into the workspace, or push its commits. */
    public static final Icon PUSH = AllIcons.Vcs.Push;

    /** Dataform Tools: the chevron after the name of a picker. */
    public static final Icon CHEVRON_DOWN = AllIcons.General.ChevronDown;

    /** Dataform Tools: create a workspace. */
    public static final Icon ADD = AllIcons.General.Add;

    /** Dataform Tools options menu: Manage Repositories, the repository in the colour of the menu icons. */
    public static final Icon MANAGE_REPOSITORIES = IconLoader.getIcon(
            "/icons/manageRepositories.svg",
            DataformIcons.class
    );

    /** Dataform Tools file menu: Compare with Local. */
    public static final Icon DIFF = AllIcons.Actions.Diff;

    /** Dataform Tools file menu: Fetch to Local. */
    public static final Icon FETCH = AllIcons.Vcs.Fetch;

    /** Dataform Tools options menu: sign in to or sign out of Google Cloud. */
    public static final Icon ACCOUNT = AllIcons.General.User;
}
