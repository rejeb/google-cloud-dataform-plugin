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

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotificationProvider;
import io.github.rejeb.dataform.language.util.Utils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import java.util.function.Function;

/**
 * Shows a persistent banner in every file of a Dataform project when a Google sign-in is required.
 * The banner is the
 * only place from which the sign-in flow can start automatically-visible: it never signs in by
 * itself, the user must click.
 */
public final class DataformAuthEditorNotificationProvider
        implements EditorNotificationProvider, DumbAware {

    @Override
    public @Nullable Function<? super FileEditor, ? extends JComponent> collectNotificationData(
            @NotNull Project project,
            @NotNull VirtualFile file
    ) {
        if (!Utils.isDataformProject(project)) {
            return null;
        }
        DataformAuthState state = DataformAuthState.getInstance();
        if (!state.isBannerVisible()) {
            return null;
        }
        AuthStatus status = state.getStatus();
        String error = state.getLastError();
        return fileEditor -> createPanel(fileEditor, project, status, error);
    }

    private static EditorNotificationPanel createPanel(
            @NotNull FileEditor fileEditor,
            @NotNull Project project,
            @NotNull AuthStatus status,
            @Nullable String error
    ) {
        EditorNotificationPanel panel =
                new EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Warning);

        if (status == AuthStatus.IN_PROGRESS) {
            panel.setText("Signing in to Google Cloud, complete the flow in your browser...");
            return panel;
        }

        if (!OAuthClientConfig.isConfigured()) {
            panel.setText("Dataform cannot sign in to Google Cloud: the OAuth client was overridden "
                    + "with an empty value. Fix the dataform.plugin.oauth.clientId and "
                    + "dataform.plugin.oauth.clientSecret system properties, or remove them to use "
                    + "the default client.");
            panel.createActionLabel("Close",
                    () -> DataformAuthState.getInstance().dismissBanner());
            return panel;
        }

        String message = "Dataform needs a Google Cloud sign-in to reach BigQuery and the Dataform API.";
        panel.setText(error == null ? message : message + " Last attempt failed: " + error);
        panel.createActionLabel("Sign in to Google Cloud",
                () -> DataformCredentialsService.getInstance().signIn(project));
        panel.createActionLabel("Close",
                () -> DataformAuthState.getInstance().dismissBanner());
        return panel;
    }
}
