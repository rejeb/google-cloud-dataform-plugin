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
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBusConnection;
import io.github.rejeb.dataform.language.compilation.DataformCompilationEvent;
import io.github.rejeb.dataform.language.lineage.model.LineageModel;
import io.github.rejeb.dataform.language.schema.sql.DataformSchemaEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import java.beans.PropertyChangeListener;

/**
 * File editor hosting the project-wide {@link LineageProjectPanel}.
 * Refreshes the graph every time the editor tab is selected and after every compilation and schema
 * refresh, which every save of a source triggers.
 */
public final class LineageFileEditor extends UserDataHolderBase implements FileEditor {

    private final VirtualFile file;
    private final LineageModel model;
    private final LineageProjectPanel panel;
    private final MessageBusConnection connection;

    public LineageFileEditor(@NotNull Project project, @NotNull VirtualFile file) {
        this.file = file;
        this.model = new LineageModel(project);
        this.panel = new LineageProjectPanel(project, model);

        this.connection = project.getMessageBus().connect();
        this.connection.subscribe(DataformCompilationEvent.TOPIC, (DataformCompilationEvent) () -> panel.refresh(false));
        this.connection.subscribe(DataformSchemaEvent.TOPIC, (DataformSchemaEvent) () -> panel.refresh(false));
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
        connection.disconnect();
    }
}
