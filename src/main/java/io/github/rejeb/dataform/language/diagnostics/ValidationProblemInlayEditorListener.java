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
package io.github.rejeb.dataform.language.diagnostics;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.project.Project;
import io.github.rejeb.dataform.language.validation.ValidationRefreshListener;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Paints the validation chips as soon as an editor is opened, and installs the document
 * listener that keeps them current while typing.
 */
public final class ValidationProblemInlayEditorListener implements EditorFactoryListener {

    private static final AtomicBoolean DOCUMENT_LISTENER_INSTALLED = new AtomicBoolean();

    @Override
    public void editorCreated(@NotNull EditorFactoryEvent event) {
        Editor editor = event.getEditor();
        Project project = editor.getProject();
        if (project == null || project.isDisposed()) {
            return;
        }
        installDocumentListener();
        ValidationProblemInlayManager.getInstance(project).refresh(editor);
    }

    private static void installDocumentListener() {
        if (!DOCUMENT_LISTENER_INSTALLED.compareAndSet(false, true)) {
            return;
        }
        EditorFactory.getInstance().getEventMulticaster().addDocumentListener(
                new ValidationRefreshListener(), ApplicationManager.getApplication());
    }
}
