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
package io.github.rejeb.dataform.language.completion;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import io.github.rejeb.dataform.language.injection.SqlxJsQueryInjector;
import io.github.rejeb.dataform.language.psi.SqlxFile;
import org.jetbrains.annotations.NotNull;

public class SqlxDollarTypedHandler extends TypedHandlerDelegate {

    /**
     * Schedules the completion auto popup when a dollar sign is typed inside a SQLX file,
     * so that the {@code ${...}} template expressions are proposed.
     */
    @NotNull
    @Override
    public Result checkAutoPopup(char charTyped,
                                 @NotNull Project project,
                                 @NotNull Editor editor,
                                 @NotNull PsiFile file) {
        if (charTyped != '$') {
            return Result.CONTINUE;
        }
        PsiFile topLevelFile = InjectedLanguageManager.getInstance(project).getTopLevelFile(file);
        if (topLevelFile == null) {
            return Result.CONTINUE;
        }
        if (!(topLevelFile instanceof SqlxFile)
                && !SqlxJsQueryInjector.isDataformDefinitionFile(topLevelFile.getOriginalFile())) {
            return Result.CONTINUE;
        }
        AutoPopupController.getInstance(project).scheduleAutoPopup(editor);
        return Result.CONTINUE;
    }
}
