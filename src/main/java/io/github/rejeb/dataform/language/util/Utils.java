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
package io.github.rejeb.dataform.language.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.sql.dialects.bigquery.BigQueryDialect;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Utils {

    public static String formatSql(@NotNull Project project, @NotNull String sql) {
        return ApplicationManager.getApplication().runWriteIntentReadAction(() -> doFormat(project, sql));
    }

    private static String doFormat(@NotNull Project project, @NotNull String sql) {
        PsiFile file = PsiFileFactory.getInstance(project)
                .createFileFromText("temp.sql", BigQueryDialect.INSTANCE, sql);
        Runnable r = () -> CodeStyleManager.getInstance(project).reformat(file);
        WriteCommandAction.runWriteCommandAction(project,null,null, r);
        return file.getText();
    }

    public static void flushFiles(Project project) {
        ApplicationManager.getApplication().invokeAndWait(() -> {
            PsiDocumentManager.getInstance(project).commitAllDocuments();
            FileDocumentManager.getInstance().saveAllDocuments();
        });
    }

    public static boolean isActionFile(@NotNull VirtualFile file) {
        String normalizedPath = file.getPath().replace('\\', '/');
        return normalizedPath.contains("/definitions/") &&
                (normalizedPath.endsWith(".sqlx") || normalizedPath.endsWith(".js")) &&
                file.isWritable();
    }

    @NotNull
    public static String formatBytes(@Nullable Long bytes) {
        if (bytes == null || bytes < 0) return "—";
        if (bytes < 1_024) return bytes + " B";
        if (bytes < 1_048_576) return String.format("%.1f KB", bytes / 1_024.0);
        if (bytes < 1_073_741_824) return String.format("%.1f MB", bytes / 1_048_576.0);
        return String.format("%.2f GB", bytes / 1_073_741_824.0);
    }
}
