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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogExtension;
import com.intellij.util.ui.cloneDialog.VcsCloneDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Opens the Get from Version Control dialog on its Dataform tab.
 */
public final class DataformCloneDialogs {

    private DataformCloneDialogs() {
    }

    /**
     * @return whether the dialog offers the Dataform tab, which needs the Git plugin
     */
    public static boolean isAvailable() {
        return VcsCloneDialogExtension.Companion.getEP_NAME().findExtension(DataformCloneDialogExtension.class) != null;
    }

    /**
     * Shows the dialog and fetches what the user chose.
     *
     * @param project the project the dialog was asked from, {@code null} when there is none
     */
    public static void open(@Nullable Project project) {
        Project target = dialogProject(project);
        VcsCloneDialog dialog = new VcsCloneDialog.Builder(target).forExtension(DataformCloneDialogExtension.class);
        if (dialog.showAndGet()) {
            dialog.doClone(ProjectLevelVcsManager.getInstance(target).getCompositeCheckoutListener());
        }
    }

    static @NotNull Project dialogProject(@Nullable Project project) {
        if (project != null) {
            return project;
        }
        Project[] open = ProjectManager.getInstance().getOpenProjects();
        return open.length > 0 ? open[open.length - 1] : ProjectManager.getInstance().getDefaultProject();
    }
}
