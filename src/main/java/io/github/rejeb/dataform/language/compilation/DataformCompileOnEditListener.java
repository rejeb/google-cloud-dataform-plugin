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
package io.github.rejeb.dataform.language.compilation;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import io.github.rejeb.dataform.language.util.DataformProjectLayout;
import io.github.rejeb.dataform.language.util.DataformProjects;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Recompiles a Dataform project once the user has stopped editing one of its sources, without
 * waiting for a save. The compilation itself is held back by
 * {@link DataformAutoCompileService#scheduleCompileAfterEdit()} until typing stops, so it never
 * interrupts what the user is doing.
 */
public final class DataformCompileOnEditListener implements EditorFactoryListener, DocumentListener {

    private static final AtomicBoolean DOCUMENT_LISTENER_INSTALLED = new AtomicBoolean();

    @Override
    public void editorCreated(@NotNull EditorFactoryEvent event) {
        installDocumentListener();
    }

    private static void installDocumentListener() {
        if (!DOCUMENT_LISTENER_INSTALLED.compareAndSet(false, true)) {
            return;
        }
        EditorFactory.getInstance().getEventMulticaster().addDocumentListener(
                new DataformCompileOnEditListener(), ApplicationManager.getApplication());
    }

    @Override
    public void documentChanged(@NotNull DocumentEvent event) {
        VirtualFile file = FileDocumentManager.getInstance().getFile(event.getDocument());
        if (!DataformProjectLayout.isDataformSource(file)) {
            return;
        }
        DataformProjects.forEachOwning(file, project ->
                DataformAutoCompileService.getInstance(project).scheduleCompileAfterEdit());
    }
}
