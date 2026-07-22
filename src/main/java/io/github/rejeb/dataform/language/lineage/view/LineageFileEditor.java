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

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.messages.MessageBusConnection;
import io.github.rejeb.dataform.language.lineage.model.LineageModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.Timer;
import java.beans.PropertyChangeListener;
import java.util.List;

/**
 * File editor hosting the project-wide {@link LineageProjectPanel}.
 * Refreshes the graph every time the editor tab is selected.
 */
public final class LineageFileEditor implements FileEditor {

    private final Project project;
    private final VirtualFile file;
    private final LineageModel model;
    private final LineageProjectPanel panel;
    private final MessageBusConnection connection;
    private final Timer debounce;

    public LineageFileEditor(@NotNull Project project, @NotNull VirtualFile file) {
        this.project = project;
        this.file = file;
        this.model = new LineageModel(project);
        this.panel = new LineageProjectPanel(project, model);

        this.debounce = new Timer(300, e -> panel.refresh(true));
        this.debounce.setRepeats(false);

        this.connection = project.getMessageBus().connect();
        this.connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
            @Override
            public void after(@NotNull List<? extends VFileEvent> events) {
                boolean sqlxChanged = events.stream().anyMatch(event -> {
                    String path = event.getPath();
                    return path.endsWith(".sqlx") || path.endsWith(".js");
                });
                if (sqlxChanged) debounce.restart();
            }
        });
    }

    @Override
    public @NotNull VirtualFile getFile() {
        return file;
    }

    @Override
    public void selectNotify() {
        panel.refresh(false);
    }

    @Override
    public @NotNull JComponent getComponent() {
        return panel;
    }

    @Override
    public @NotNull String getName() {
        return "Dataform Lineage";
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
        return null;
    }

    @Override
    public void setState(@NotNull FileEditorState state) {
    }

    @Override
    public boolean isModified() {
        return false;
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
    }

    @Override
    public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
    }

    @Override
    public void dispose() {
        debounce.stop();
        connection.disconnect();
    }

    @Override
    public @Nullable <T> T getUserData(@NotNull Key<T> key) {
        return null;
    }

    @Override
    public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
    }
}
