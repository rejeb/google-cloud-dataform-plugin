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

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LoggedErrorProcessor;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.injected.editor.EditorWindow;
import io.github.rejeb.dataform.language.settings.DataformToolsSettings;
import io.github.rejeb.dataform.language.validation.SqlxValidationProblem;
import io.github.rejeb.dataform.language.validation.SqlxValidationService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public class ValidationProblemInlayManagerImplTest extends BasePlatformTestCase {

    private static final int MORE_EDITORS_THAN_THE_PLATFORM_TOLERATES = 130;
    private static final String TOO_MANY_READ_ACTIONS = "Too many non-blocking read actions";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        DataformToolsSettings.getInstance().setShowInlineCompilationErrors(true);
    }

    public void testManagerIsRegisteredAndResolvable() {
        assertNotNull(ValidationProblemInlayManager.getInstance(getProject()));
    }

    public void testRefreshAddsNoChipsWhenThereAreNoDiagnostics() {
        myFixture.configureByText("a.sqlx", "select 1\n");
        Editor editor = hostEditor();
        ValidationProblemInlayManager.getInstance(getProject()).refresh(editor);
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

        assertEmpty(editor.getInlayModel().getAfterLineEndElementsInRange(
                0, editor.getDocument().getTextLength()));
        assertEmpty(editor.getInlayModel().getBlockElementsInRange(
                0, editor.getDocument().getTextLength()));
    }

    public void testRefreshIsIdempotentAndDoesNotAccumulateChips() {
        myFixture.configureByText("b.sqlx", "select 1\n");
        Editor editor = hostEditor();
        ValidationProblemInlayManager manager =
                ValidationProblemInlayManager.getInstance(getProject());
        manager.refresh(editor);
        manager.refresh(editor);
        manager.refresh(editor);
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

        assertEmpty(editor.getInlayModel().getAfterLineEndElementsInRange(
                0, editor.getDocument().getTextLength()));
    }

    public void testInjectedEditorsAreSkippedWithoutError() {
        myFixture.configureByText("c.sqlx", "config { type: \"table\" }\n\nselect 1\n");
        ValidationProblemInlayManager.getInstance(getProject()).refreshAll();
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
    }

    public void testRefreshAllValidatesTheEditorsOverDataformFilesOnly() {
        List<String> validated = new CopyOnWriteArrayList<>();
        stubValidation(file -> {
            validated.add(file.getName());
            return List.of();
        });
        openEditor("definitions/a.sqlx");
        openEditor("definitions/helper.js");
        openEditor("notes.txt");
        openEditor("node_modules/dep/index.js");

        ValidationProblemInlayManager.getInstance(getProject()).refreshAll();
        drain();

        assertSameElements(new HashSet<>(validated), "a.sqlx", "helper.js");
    }

    public void testRefreshAllOverManyEditorsStaysWithinThePlatformLimit() {
        stubValidation(file -> List.of());
        List<String> errors = new CopyOnWriteArrayList<>();
        try (AccessToken ignored = LoggedErrorProcessor.executeWith(recordingTooManyReadActions(errors))) {
            for (int i = 0; i < MORE_EDITORS_THAN_THE_PLATFORM_TOLERATES; i++) {
                openEditor("definitions/action_" + i + ".sqlx");
            }
            drain();
            ValidationProblemInlayManager.getInstance(getProject()).refreshAll();
            drain();
        }

        assertEmpty(errors);
    }

    public void testChipsLeaveAnEditorWhoseFileStopsBeingADataformFile() throws IOException {
        stubValidation(file -> List.of(SqlxValidationProblem.compilationError(
                new TextRange(0, 6), "broken", null, List.of())));
        VirtualFile file = myFixture.addFileToProject("definitions/renamed.sqlx", "select 1\n").getVirtualFile();
        Editor editor = openEditorOn(file);
        drain();
        assertSize(1, chipsOf(editor));

        WriteAction.runAndWait(() -> file.rename(this, "renamed.txt"));
        ValidationProblemInlayManager.getInstance(getProject()).refresh(editor);
        drain();

        assertEmpty(chipsOf(editor));
    }

    private void stubValidation(SqlxValidationService service) {
        ServiceContainerUtil.replaceService(getProject(), SqlxValidationService.class,
                service, getTestRootDisposable());
    }

    private Editor openEditor(String path) {
        return openEditorOn(myFixture.addFileToProject(path, "select 1\n").getVirtualFile());
    }

    private Editor openEditorOn(VirtualFile file) {
        Document document = FileDocumentManager.getInstance().getDocument(file);
        assertNotNull(document);
        Editor editor = EditorFactory.getInstance().createEditor(document, getProject());
        Disposer.register(getTestRootDisposable(), () -> EditorFactory.getInstance().releaseEditor(editor));
        return editor;
    }

    private static List<Inlay<?>> chipsOf(Editor editor) {
        return List.copyOf(editor.getInlayModel().getAfterLineEndElementsInRange(
                0, editor.getDocument().getTextLength()));
    }

    private static void drain() {
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    }

    private static LoggedErrorProcessor recordingTooManyReadActions(List<String> errors) {
        return new LoggedErrorProcessor() {
            @Override
            public @NotNull Set<Action> processError(@NotNull String category,
                                                     @NotNull String message,
                                                     String @NotNull [] details,
                                                     @Nullable Throwable throwable) {
                if (!message.contains(TOO_MANY_READ_ACTIONS)) {
                    return Action.ALL;
                }
                errors.add(message);
                return EnumSet.noneOf(Action.class);
            }
        };
    }

    private Editor hostEditor() {
        Editor editor = myFixture.getEditor();
        return editor instanceof EditorWindow window ? window.getDelegate() : editor;
    }
}
