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

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import io.github.rejeb.dataform.language.gcp.auth.AuthStatus;
import io.github.rejeb.dataform.language.gcp.auth.DataformAuthState;
import io.github.rejeb.dataform.language.gcp.auth.DataformCredentialsService;
import org.jetbrains.annotations.NotNull;

/**
 * Toggles the Google Cloud sign-in from the Dataform tool window title bar. Signing in is only
 * ever started by this explicit click.
 */
public final class GcpAuthAction extends AnAction implements DumbAware {

    private final Project project;

    public GcpAuthAction(@NotNull Project project) {
        this.project = project;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        boolean signedIn = DataformCredentialsService.getInstance().isSignedIn();
        boolean inProgress = DataformAuthState.getInstance().getStatus() == AuthStatus.IN_PROGRESS;
        String email = DataformCredentialsService.getInstance().getAccountEmail();

        e.getPresentation().setIcon(signedIn
                ? AllIcons.Actions.Exit
                : AllIcons.General.User);
        e.getPresentation().setText(signedIn
                ? (email != null ? "Sign Out of Google Cloud (" + email + ")" : "Sign Out of Google Cloud")
                : "Sign In to Google Cloud");
        e.getPresentation().setEnabled(!inProgress);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        DataformCredentialsService service = DataformCredentialsService.getInstance();
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            if (service.isSignedIn()) {
                service.signOut();
            } else {
                service.signIn(project);
            }
        });
    }
}
