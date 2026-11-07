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

import com.intellij.notification.NotificationType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.CheckoutProvider;
import com.intellij.openapi.wm.impl.welcomeScreen.cloneableProjects.CloneableProjectsService.CloneStatus;
import com.intellij.openapi.wm.impl.welcomeScreen.cloneableProjects.CloneableProjectsService.CloneTask;
import com.intellij.openapi.wm.impl.welcomeScreen.cloneableProjects.CloneableProjectsService.CloneTaskInfo;
import io.github.rejeb.dataform.language.gcp.common.GcpApiException;
import io.github.rejeb.dataform.language.util.DataformNotifications;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * Runs a {@link WorkspaceDownload} the way the platform runs a clone: the Welcome screen and the
 * status bar show its progress, and a cancelled or failed download is reported on its row.
 */
final class WorkspaceDownloadTask implements CloneTask {

    private static final Logger LOG = Logger.getInstance(WorkspaceDownloadTask.class);

    private final WorkspaceDownload download;
    private final RepositoryCheckoutRequest request;
    private final CheckoutProvider.Listener listener;
    private final @Nullable Project notifiedProject;

    /**
     * @param notifiedProject the project shown the failures, {@code null} when none is open
     */
    WorkspaceDownloadTask(@NotNull WorkspaceDownload download,
                          @NotNull RepositoryCheckoutRequest request,
                          @NotNull CheckoutProvider.Listener listener,
                          @Nullable Project notifiedProject) {
        this.download = download;
        this.request = request;
        this.listener = listener;
        this.notifiedProject = notifiedProject;
    }

    @Override
    public @NotNull CloneTaskInfo taskInfo() {
        String name = request.repository().repositoryId();
        return new CloneTaskInfo("Downloading " + name,
                "Cancel the download",
                "Download Dataform Repository",
                "Downloading " + name,
                "Download failed",
                "Download canceled",
                "Stop Downloading",
                "Stop downloading " + name + "?");
    }

    @Override
    public @NotNull CloneStatus run(@NotNull ProgressIndicator indicator) {
        try {
            download.run(request, indicator);
            listener.checkoutCompleted();
            return CloneStatus.SUCCESS;
        } catch (GcpApiException | IOException e) {
            LOG.warn("Unable to download Dataform repository " + request.repository().repositoryId(), e);
            DataformNotifications.create("Dataform repository not downloaded",
                            StringUtil.escapeXmlEntities(StringUtil.notNullize(e.getMessage())),
                            NotificationType.ERROR)
                    .notify(notifiedProject);
            return CloneStatus.FAILURE;
        }
    }
}
