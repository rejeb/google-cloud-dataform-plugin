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
package io.github.rejeb.dataform.language.action;

import com.intellij.ide.IdeView;
import com.intellij.ide.actions.CreateFileFromTemplateAction;
import com.intellij.ide.actions.CreateFileFromTemplateDialog;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import io.github.rejeb.dataform.language.DataformIcons;
import io.github.rejeb.dataform.language.util.DataformProjectLayout;
import io.github.rejeb.dataform.language.util.Utils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CreateSqlxFileAction extends CreateFileFromTemplateAction implements DumbAware {

    private static final String DIALOG_TITLE = "New SQLX File";
    private static final String LAST_USED_TEMPLATE_PROPERTY = "Dataform.NewSqlxFile.template";

    @Override
    protected void buildDialog(@NotNull Project project,
                               @NotNull PsiDirectory directory,
                               @NotNull CreateFileFromTemplateDialog.Builder builder) {
        builder.setTitle(DIALOG_TITLE);
        for (SqlxFileTemplate template : SqlxFileTemplate.values()) {
            builder.addKind(template.getKind(), DataformIcons.FILE, template.getTemplateName());
        }
    }

    @Override
    protected String getActionName(PsiDirectory directory, @NotNull String newName, String templateName) {
        return "Create SQLX File " + newName;
    }

    @Override
    @Nullable
    protected String getDefaultTemplateProperty() {
        return LAST_USED_TEMPLATE_PROPERTY;
    }

    @Override
    protected boolean isAvailable(@NotNull DataContext dataContext) {
        if (!super.isAvailable(dataContext)) {
            return false;
        }
        Project project = CommonDataKeys.PROJECT.getData(dataContext);
        if (project == null) {
            return false;
        }
        return Utils.isDataformProject(project) || isInDataformProject(dataContext);
    }

    private static boolean isInDataformProject(@NotNull DataContext dataContext) {
        IdeView view = LangDataKeys.IDE_VIEW.getData(dataContext);
        if (view == null) {
            return false;
        }
        for (PsiDirectory directory : view.getDirectories()) {
            if (DataformProjectLayout.isInDataformProject(directory.getVirtualFile())) {
                return true;
            }
        }
        return false;
    }
}
