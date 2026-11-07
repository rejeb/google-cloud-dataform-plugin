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
package io.github.rejeb.dataform.language.gcp.toolwindow.action;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import io.github.rejeb.dataform.language.DataformIcons;
import io.github.rejeb.dataform.language.gcp.common.DataformConsoleUrls;
import io.github.rejeb.dataform.language.gcp.settings.DataformRepositoryConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Opens the selected workspace in the Dataform console, or the repository when its main branch is
 * selected; given a file, opens that file of the workspace.
 */
public final class OpenInConsoleAction extends DumbAwareAction {

    private final Supplier<@Nullable DataformRepositoryConfig> repository;
    private final Supplier<@Nullable String> workspace;
    private final Supplier<@Nullable String> file;

    /**
     * The action opening the page of {@code workspace} in {@code repository}, read when it runs.
     */
    public OpenInConsoleAction(@NotNull Supplier<@Nullable DataformRepositoryConfig> repository,
                               @NotNull Supplier<@Nullable String> workspace) {
        this(repository, workspace, () -> null);
    }

    /**
     * The action opening {@code file} of {@code workspace} in {@code repository}, read on the EDT
     * when it runs; without a file or a workspace it opens the workspace or the repository.
     */
    public OpenInConsoleAction(@NotNull Supplier<@Nullable DataformRepositoryConfig> repository,
                               @NotNull Supplier<@Nullable String> workspace,
                               @NotNull Supplier<@Nullable String> file) {
        super("Open in Google Cloud Console", null, DataformIcons.EXTERNAL_LINK);
        this.repository = repository;
        this.workspace = workspace;
        this.file = file;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        DataformRepositoryConfig config = repository.get();
        e.getPresentation().setEnabled(config != null);
        e.getPresentation().setDescription(config == null ? null : description(config, workspace.get(), file.get()));
    }

    private static @NotNull String description(@NotNull DataformRepositoryConfig config, @Nullable String workspaceId,
                                               @Nullable String filePath) {
        if (workspaceId == null) {
            return "Open repository " + config.displayName() + " in the Dataform console";
        }
        return "Open " + (filePath == null ? "workspace " + workspaceId : filePath) + " in the Dataform console";
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        String url = url();
        if (url != null) {
            BrowserUtil.browse(url);
        }
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }

    /**
     * The console page the action opens now, or null while no repository is active.
     */
    public @Nullable String url() {
        DataformRepositoryConfig config = repository.get();
        if (config == null) {
            return null;
        }
        String workspaceId = workspace.get();
        String filePath = file.get();
        return workspaceId != null && filePath != null
                ? DataformConsoleUrls.ofFile(config, workspaceId, filePath)
                : DataformConsoleUrls.of(config, workspaceId);
    }
}
