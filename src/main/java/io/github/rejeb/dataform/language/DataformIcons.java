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

import com.intellij.openapi.util.IconLoader;
import io.github.rejeb.dataform.language.ui.RotatingIcons;

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
    public static final Icon LAYOUT_EDITOR_ONLY = IconLoader.getIcon(
            "/icons/layoutEditorOnly.svg",
            DataformIcons.class
    );

    /** SQLX editor toolbar: show the editor and the preview side by side. */
    public static final Icon LAYOUT_EDITOR_AND_PREVIEW = IconLoader.getIcon(
            "/icons/layoutEditorAndPreview.svg",
            DataformIcons.class
    );

    /** SQLX editor toolbar: show only the preview. */
    public static final Icon LAYOUT_PREVIEW_ONLY = IconLoader.getIcon(
            "/icons/layoutPreviewOnly.svg",
            DataformIcons.class
    );

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
    public static final Icon COMPILE = IconLoader.getIcon(
            "/icons/compile.svg",
            DataformIcons.class
    );

    /** Services view: the root of the queries run from the SQLX preview. */
    public static final Icon QUERIES = IconLoader.getIcon(
            "/icons/queries.svg",
            DataformIcons.class
    );

    /** SQLX editor toolbar: run the query, the action or the test of the file. */
    public static final Icon RUN = IconLoader.getIcon(
            "/icons/run.svg",
            DataformIcons.class
    );

    /** Status of a run: succeeded. */
    public static final Icon STATUS_SUCCEEDED = IconLoader.getIcon(
            "/icons/statusSucceeded.svg",
            DataformIcons.class
    );

    /** Status of a run: failed. */
    public static final Icon STATUS_FAILED = IconLoader.getIcon(
            "/icons/statusFailed.svg",
            DataformIcons.class
    );

    /** Status of a run: cancelled. */
    public static final Icon STATUS_CANCELLED = IconLoader.getIcon(
            "/icons/statusCancelled.svg",
            DataformIcons.class
    );

    /** Status of a workflow action: skipped or disabled. */
    public static final Icon STATUS_SKIPPED = IconLoader.getIcon(
            "/icons/statusSkipped.svg",
            DataformIcons.class
    );

    /** Status of a workflow action: waiting to run. */
    public static final Icon STATUS_PENDING = IconLoader.getIcon(
            "/icons/statusPending.svg",
            DataformIcons.class
    );

    /** Run console toolbar: Show Failed Only. */
    public static final Icon FAILED_ONLY = IconLoader.getIcon(
            "/icons/failedOnly.svg",
            DataformIcons.class
    );

    /** Stops a running query or workflow. */
    public static final Icon STOP = IconLoader.getIcon(
            "/icons/stop.svg",
            DataformIcons.class
    );

    /** Removes finished items from a list. */
    public static final Icon TRASH = IconLoader.getIcon(
            "/icons/trash.svg",
            DataformIcons.class
    );

    /** Exports a result. */
    public static final Icon DOWNLOAD = IconLoader.getIcon(
            "/icons/download.svg",
            DataformIcons.class
    );

    /** Results pager: the first page. */
    public static final Icon PAGE_FIRST = IconLoader.getIcon(
            "/icons/pageFirst.svg",
            DataformIcons.class
    );

    /** Results pager: the previous page. */
    public static final Icon PAGE_PREVIOUS = IconLoader.getIcon(
            "/icons/pagePrevious.svg",
            DataformIcons.class
    );

    /** Results pager: the next page. */
    public static final Icon PAGE_NEXT = IconLoader.getIcon(
            "/icons/pageNext.svg",
            DataformIcons.class
    );

    /** Results pager: the last page. */
    public static final Icon PAGE_LAST = IconLoader.getIcon(
            "/icons/pageLast.svg",
            DataformIcons.class
    );

    /** Status of a run: running, the prototype spinner turning. */
    public static final Icon SPINNER = RotatingIcons.spinner(IconLoader.getIcon(
            "/icons/spinner.svg",
            DataformIcons.class
    ));

    /** Query preview: a table, a view or an incremental table, in blue. */
    public static final Icon ACTION_TABLE = IconLoader.getIcon(
            "/icons/actionTable.svg",
            DataformIcons.class
    );

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
    public static final Icon SCHEMA_COLUMN = IconLoader.getIcon(
            "/icons/schemaColumn.svg",
            DataformIcons.class
    );

    /** Schema preview: a RECORD column, holding fields. */
    public static final Icon SCHEMA_RECORD = IconLoader.getIcon(
            "/icons/schemaRecord.svg",
            DataformIcons.class
    );

    /** Schema preview: Refresh schema. */
    public static final Icon REFRESH = IconLoader.getIcon(
            "/icons/refresh.svg",
            DataformIcons.class
    );

    /** Query preview: Copy SQL. */
    public static final Icon COPY = IconLoader.getIcon(
            "/icons/copy.svg",
            DataformIcons.class
    );

    /** Query preview: Open in BigQuery Console. */
    public static final Icon EXTERNAL_LINK = IconLoader.getIcon(
            "/icons/externalLink.svg",
            DataformIcons.class
    );

    /** Dataform Tools: a Dataform repository. */
    public static final Icon REPOSITORY = IconLoader.getIcon(
            "/icons/repository.svg",
            DataformIcons.class
    );

    /** Dataform Tools: a workspace of a repository. */
    public static final Icon BRANCH = IconLoader.getIcon(
            "/icons/branch.svg",
            DataformIcons.class
    );

    /** Dataform Tools: copy the workspace files into the project. */
    public static final Icon PULL = IconLoader.getIcon(
            "/icons/pull.svg",
            DataformIcons.class
    );

    /** Dataform Tools: copy the project files into the workspace, or push its commits. */
    public static final Icon PUSH = IconLoader.getIcon(
            "/icons/push.svg",
            DataformIcons.class
    );

    /** Dataform Tools: the chevron after the name of a picker, four pixels from its left edge. */
    public static final Icon CHEVRON_DOWN = IconLoader.getIcon(
            "/icons/chevronDown.svg",
            DataformIcons.class
    );

    /** Dataform Tools: create a workspace. */
    public static final Icon ADD = IconLoader.getIcon(
            "/icons/add.svg",
            DataformIcons.class
    );

    /** Dataform Tools options menu: Manage Repositories, the repository in the colour of the menu icons. */
    public static final Icon MANAGE_REPOSITORIES = IconLoader.getIcon(
            "/icons/manageRepositories.svg",
            DataformIcons.class
    );

    /** Dataform Tools file menu: Compare with Local. */
    public static final Icon DIFF = IconLoader.getIcon(
            "/icons/diff.svg",
            DataformIcons.class
    );

    /** Dataform Tools file menu: Fetch to Local. */
    public static final Icon FETCH = IconLoader.getIcon(
            "/icons/fetch.svg",
            DataformIcons.class
    );

    /** Dataform Tools options menu: sign in to or sign out of Google Cloud. */
    public static final Icon ACCOUNT = IconLoader.getIcon(
            "/icons/account.svg",
            DataformIcons.class
    );
}
