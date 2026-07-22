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
package io.github.rejeb.dataform.language.lineage.view;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import io.github.rejeb.dataform.language.gcp.execution.workflow.runconfig.RunSqlxHelper;
import io.github.rejeb.dataform.language.lineage.graph.LineageNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Shared navigation/execution helpers for lineage nodes: opening the source SQLX and
 * launching the Dataform run configuration. File resolution runs off the EDT; UI work is
 * marshalled back via {@code invokeLater}.
 */
final class LineageActions {

    private LineageActions() {
    }

    static void openSource(@NotNull Project project, @Nullable LineageNode node) {
        withFile(project, node, vf -> FileEditorManager.getInstance(project)
                .openEditor(new OpenFileDescriptor(project, vf), true));
    }

    static void runAction(@NotNull Project project, @Nullable LineageNode node) {
        if (node == null) return;
        RunSqlxHelper.launchAction(project, node.fullName(), node.name());
    }

    private static void withFile(@NotNull Project project, @Nullable LineageNode node,
                                 @NotNull Consumer<VirtualFile> onEdt) {
        if (node == null || node.fileName() == null) return;
        String basePath = project.getBasePath();
        if (basePath == null) return;
        String absolutePath = basePath + "/" + node.fileName();
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByPath(absolutePath);
            if (vf == null) return;
            ApplicationManager.getApplication().invokeLater(() -> onEdt.accept(vf));
        });
    }
}
