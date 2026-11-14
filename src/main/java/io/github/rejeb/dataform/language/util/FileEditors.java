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

import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * Opens files in editor tabs through file navigation. The synchronous {@code openFile} and
 * {@code openEditor} of {@code FileEditorManager} are deprecated from 2026.3, and the API that
 * replaces them does not exist in 2026.2; navigation opens the file the same way in both.
 */
public final class FileEditors {

    private FileEditors() {
    }

    /**
     * Opens the file in an editor tab, or selects the tab already showing it, and focuses it.
     * Must be called on the event dispatch thread.
     *
     * @return whether an editor shows the file
     */
    public static boolean open(@NotNull Project project, @NotNull VirtualFile file) {
        return new OpenFileDescriptor(project, file).navigateInEditor(project, true);
    }
}
