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

import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogExtension;
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogExtensionComponent;
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogExtensionStatusLine;
import com.intellij.util.concurrency.AppExecutorUtil;
import io.github.rejeb.dataform.language.DataformIcons;
import io.github.rejeb.dataform.language.gcp.auth.DataformCredentialsService;
import io.github.rejeb.dataform.language.gcp.workspace.repository.GcpDataformWorkspaceRepository;
import io.github.rejeb.dataform.language.gcp.workspace.repository.WorkspaceRepository;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;
import java.nio.file.Path;
import java.util.List;

/**
 * Adds the Dataform tab to the Get from Version Control dialog.
 */
public final class DataformCloneDialogExtension implements VcsCloneDialogExtension {

    /**
     * @return the name of the tab
     */
    @Override
    public @NotNull String getName() {
        return "Dataform";
    }

    /**
     * @return the icon of the tab
     */
    @Override
    public @NotNull Icon getIcon() {
        return DataformIcons.FILE;
    }

    /**
     * @return the signed-in account when it is known; the tab shows the rest of the sign-in state
     */
    @Override
    public @NotNull List<VcsCloneDialogExtensionStatusLine> getAdditionalStatusLines() {
        String account = DataformCredentialsService.getInstance().getAccountEmail();
        return account == null
                ? List.of()
                : List.of(VcsCloneDialogExtensionStatusLine.Companion.greyText(account));
    }

    /**
     * @return the content of the tab
     */
    @Override
    public @NotNull VcsCloneDialogExtensionComponent createMainComponent(@NotNull Project project,
                                                                         @NotNull ModalityState modalityState) {
        WorkspaceRepository repository = new GcpDataformWorkspaceRepository();
        RepositoryCheckoutStarter starter = new DefaultRepositoryCheckoutStarter(project, repository,
                PendingRepositoryLinks.getInstance(), directory -> open(project, directory));
        return new DataformCloneDialogComponent(project, modalityState, repository,
                DataformCredentialsService.getInstance(), starter, AppExecutorUtil.getAppExecutorService());
    }

    private static void open(@NotNull Project project, @NotNull Path directory) {
        ProjectUtil.openOrImport(directory, project.isDefault() ? null : project, false);
    }
}
