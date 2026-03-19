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
package io.github.rejeb.dataform.language.fileEditor;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class SqlxSplitEditorProvider implements FileEditorProvider, DumbAware {

    @Override
    public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
        String normalizedPath = file.getPath().replace('\\', '/');
        return normalizedPath.contains("/definitions/") &&
                (normalizedPath.endsWith(".sqlx") || normalizedPath.endsWith(".js")) &&
                file.isWritable();
    }

    @Override
    public @NotNull FileEditor createEditor(@NotNull Project project,
                                            @NotNull VirtualFile file) {
        TextEditor textEditor = (TextEditor)
                TextEditorProvider.getInstance().createEditor(project, file);

        SqlxCompiledPreviewEditor preview =
                new SqlxCompiledPreviewEditor(project, file);

        return new SqlxSplitEditor(textEditor, preview);
    }

    @Override
    public @NotNull String getEditorTypeId() {
        return "sqlx-split-editor";
    }

    @Override
    public @NotNull FileEditorPolicy getPolicy() {
        return FileEditorPolicy.HIDE_DEFAULT_EDITOR;
    }


}