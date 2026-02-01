/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
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
package io.github.rejeb.dataform.language.listener;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.sql.dialects.SqlDialectMappings;
import com.intellij.sql.dialects.bigquery.BigQueryDialect;
import com.intellij.util.concurrency.AppExecutorUtil;
import io.github.rejeb.dataform.language.SqlxFileType;
import org.jetbrains.annotations.NotNull;

public class SqlxFileTypeListener implements FileEditorManagerListener {

    @Override
    public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        if (SqlxFileType.EXTENSION.equals(file.getExtension())) {
            Project project = source.getProject();

            // Exécuter la recherche PSI en background pour éviter les opérations lentes sur EDT
            ReadAction.nonBlocking(() -> {
                        if (!project.isDisposed()) {
                            return PsiManager.getInstance(project).findFile(file);
                        }
                        return null;
                    })
                    .finishOnUiThread(com.intellij.openapi.application.ModalityState.nonModal(), psiFile -> {
                        if (psiFile != null && !project.isDisposed()) {
                            SqlDialectMappings mappings = SqlDialectMappings.getInstance(project);
                            mappings.setMapping(file, BigQueryDialect.INSTANCE);
                        }
                    })
                    .submit(AppExecutorUtil.getAppExecutorService());
        }
    }
}