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
package io.github.rejeb.dataform.language.gcp.auth;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.ui.EditorNotifications;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DataformAuthStateImpl implements DataformAuthState {

    private volatile AuthStatus status = AuthStatus.NONE;
    private volatile boolean dismissed = false;
    private volatile String lastError = null;

    @Override
    public @NotNull AuthStatus getStatus() {
        return status;
    }

    @Override
    public boolean isBannerVisible() {
        return status != AuthStatus.NONE && !dismissed;
    }

    @Override
    public @Nullable String getLastError() {
        return lastError;
    }

    @Override
    public synchronized void markAuthRequired(@NotNull AuthTrigger trigger) {
        if (status == AuthStatus.IN_PROGRESS) {
            return;
        }
        if (trigger == AuthTrigger.USER_ACTION) {
            dismissed = false;
        }
        boolean changed = status != AuthStatus.REQUIRED;
        status = AuthStatus.REQUIRED;
        if (changed || trigger == AuthTrigger.USER_ACTION) {
            refreshBanners();
        }
    }

    @Override
    public synchronized void markSignInStarted() {
        status = AuthStatus.IN_PROGRESS;
        dismissed = false;
        lastError = null;
        refreshBanners();
    }

    @Override
    public synchronized void markSignInFailed(@Nullable String message) {
        status = AuthStatus.REQUIRED;
        lastError = message;
        refreshBanners();
    }

    @Override
    public synchronized void markAuthenticated() {
        status = AuthStatus.NONE;
        dismissed = false;
        lastError = null;
        refreshBanners();
    }

    @Override
    public synchronized void dismissBanner() {
        dismissed = true;
        refreshBanners();
    }

    private void refreshBanners() {
        ApplicationManager.getApplication().invokeLater(() -> {
            for (Project project : ProjectManager.getInstance().getOpenProjects()) {
                if (!project.isDisposed()) {
                    EditorNotifications.getInstance(project).updateAllNotifications();
                }
            }
        });
    }
}
