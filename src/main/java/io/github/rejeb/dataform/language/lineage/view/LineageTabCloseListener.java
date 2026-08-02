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

import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectCloseListener;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * Closes the lineage tab before the project workspace is saved. The lineage editor is keyed by an
 * in-memory file whose {@code mock://} URL no registered virtual file system can resolve, so a tab
 * left open would be written to the workspace and fail to reopen on the next start with
 * "No file exists: mock:///Dataform Lineage". The view is computed on demand and carries no
 * persistent state, so dropping the tab on close loses nothing.
 */
public final class LineageTabCloseListener implements ProjectCloseListener {

    @Override
    public void projectClosingBeforeSave(@NotNull Project project) {
        FileEditorManager manager = project.getServiceIfCreated(FileEditorManager.class);
        if (manager == null) return;
        VirtualFile file = LineageProjectVirtualFile.getInstance();
        if (manager.isFileOpen(file)) manager.closeFile(file);
    }
}
