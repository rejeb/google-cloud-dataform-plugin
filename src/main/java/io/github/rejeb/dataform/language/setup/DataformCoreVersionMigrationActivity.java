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
package io.github.rejeb.dataform.language.setup;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.startup.ProjectActivity;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import io.github.rejeb.dataform.language.util.DataformNotifications;
import io.github.rejeb.dataform.language.util.DataformProjectLayout;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Suggests moving {@code dataformCoreVersion} from {@code workflow_settings.yaml} to {@code package.json}
 * when a Dataform project is opened.
 */
public final class DataformCoreVersionMigrationActivity implements ProjectActivity {

    static final String DISMISSED_KEY = "dataform.coreVersionMigration.dismissed";

    @Override
    public @Nullable Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        if (PropertiesComponent.getInstance(project).getBoolean(DISMISSED_KEY)) {
            return null;
        }
        VirtualFile baseDir = ProjectUtil.guessProjectDir(project);
        VirtualFile workflowSettings = baseDir == null
                ? null
                : baseDir.findChild(DataformProjectLayout.WORKFLOW_SETTINGS_YAML);
        if (workflowSettings == null || workflowSettings.isDirectory()) {
            return null;
        }
        Optional<String> version = ReadAction.nonBlocking(() ->
                        DataformCoreVersionMigrator.getInstance(project).findDeclaredCoreVersion(workflowSettings))
                .expireWith(project)
                .executeSynchronously();
        version.ifPresent(v -> ApplicationManager.getApplication().invokeLater(
                () -> suggestMigration(project, workflowSettings, v), project.getDisposed()));
        return null;
    }

    private static void suggestMigration(@NotNull Project project,
                                         @NotNull VirtualFile workflowSettings,
                                         @NotNull String version) {
        DataformNotifications.create("Dataform core version in workflow_settings.yaml",
                        "This project pins <code>@dataform/core</code> "
                                + StringUtil.escapeXmlEntities(version)
                                + " with <code>dataformCoreVersion</code>. Declare it in <code>package.json</code>"
                                + " to manage Dataform packages with npm.",
                        NotificationType.INFORMATION)
                .addAction(NotificationAction.createSimpleExpiring("Move to package.json", () -> {
                    if (!DataformCoreVersionMigrator.getInstance(project).migrate(workflowSettings)) {
                        showMigrationFailure(project);
                    }
                }))
                .addAction(NotificationAction.createSimpleExpiring("Don't ask again",
                        () -> PropertiesComponent.getInstance(project).setValue(DISMISSED_KEY, true)))
                .notify(project);
    }

    private static void showMigrationFailure(@NotNull Project project) {
        DataformNotifications.create("Dataform core version not moved",
                        "The <code>dependencies</code> of <code>package.json</code> could not be edited."
                                + " Move <code>dataformCoreVersion</code> manually.",
                        NotificationType.WARNING)
                .notify(project);
    }
}
