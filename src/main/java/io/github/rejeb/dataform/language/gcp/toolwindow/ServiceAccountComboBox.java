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
package io.github.rejeb.dataform.language.gcp.toolwindow;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import io.github.rejeb.dataform.language.gcp.common.ServiceAccountLister;
import org.jetbrains.annotations.NotNull;

import javax.swing.DefaultComboBoxModel;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.List;
import java.util.function.Supplier;

/**
 * Editable combo box that suggests the service accounts of the current GCP project.
 *
 * <p>When the field gains focus the service accounts of the project returned by the supplier are
 * fetched in the background and, if any are available, the drop-down is opened. When the caller
 * lacks the permission to list service accounts the drop-down stays closed and the component behaves
 * as a plain text field. The remote listing for a given project id is performed at most once.
 */
public class ServiceAccountComboBox extends ComboBox<String> {

    private final Project project;
    private final Supplier<String> projectIdSupplier;

    private String loadedProjectId;
    private boolean loading;
    private long generation;

    public ServiceAccountComboBox(@NotNull Project project, @NotNull Supplier<String> projectIdSupplier) {
        this.project = project;
        this.projectIdSupplier = projectIdSupplier;
        setEditable(true);

        Component editorComponent = getEditor().getEditorComponent();
        editorComponent.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                onFocusGained();
            }
        });
    }

    /**
     * @return the current text of the editor, never {@code null}
     */
    @NotNull
    public String getText() {
        Object item = getEditor().getItem();
        return item != null ? item.toString().trim() : "";
    }

    /**
     * Sets the editor text without triggering a remote listing.
     */
    public void setText(@NotNull String text) {
        setSelectedItem(text);
    }

    private void onFocusGained() {
        String projectId = projectIdSupplier.get();
        if (projectId == null || projectId.isBlank()) {
            return;
        }
        if (projectId.equals(loadedProjectId)) {
            if (getModel().getSize() > 0) {
                showPopup();
            }
            return;
        }
        if (loading) {
            return;
        }
        loading = true;
        long token = ++generation;
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            ServiceAccountLister.Result result = ServiceAccountLister.list(projectId);
            SwingUtilities.invokeLater(() -> applyResult(token, projectId, result));
        });
    }

    private void applyResult(long token,
                             @NotNull String projectId,
                             @NotNull ServiceAccountLister.Result result) {
        if (token != generation) {
            return;
        }
        loading = false;
        loadedProjectId = projectId;
        if (result.permissionDenied() || result.emails().isEmpty()) {
            return;
        }
        String current = getText();
        List<String> emails = result.emails();
        setModel(new DefaultComboBoxModel<>(emails.toArray(new String[0])));
        restoreEditorText(current);
        if (isFocusOwnerWithinEditor()) {
            showPopup();
        }
    }

    private void restoreEditorText(@NotNull String text) {
        setSelectedItem(text);
        Component editorComponent = getEditor().getEditorComponent();
        if (editorComponent instanceof JTextComponent textComponent) {
            textComponent.setText(text);
        }
    }

    private boolean isFocusOwnerWithinEditor() {
        Component editorComponent = getEditor().getEditorComponent();
        return editorComponent.isFocusOwner() || isFocusOwner();
    }
}
