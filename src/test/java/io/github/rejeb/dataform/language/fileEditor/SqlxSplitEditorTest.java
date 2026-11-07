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
package io.github.rejeb.dataform.language.fileEditor;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.actionSystem.Toggleable;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.fileEditor.TextEditorWithPreview.Layout;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.DataformIcons;
import io.github.rejeb.dataform.language.compilation.DataformBuildContext;
import io.github.rejeb.dataform.language.fileEditor.SqlxCompiledPreviewEditor.View;

import java.util.ArrayList;
import java.util.List;

public class SqlxSplitEditorTest extends BasePlatformTestCase {

    private static final String LAYOUT_PROPERTY = "SQLX EditorLayout";

    private SqlxSplitEditor editor;
    private SqlxCompiledPreviewEditor preview;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        VirtualFile file = myFixture.addFileToProject("definitions/orders.sqlx",
                "config { type: \"table\" }\nSELECT 1 AS id").getVirtualFile();
        editor = (SqlxSplitEditor) new SqlxSplitEditorProvider().createEditor(getProject(), file);
        preview = (SqlxCompiledPreviewEditor) editor.getPreviewEditor();
        editor.getComponent();
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            Disposer.dispose(editor);
            PropertiesComponent.getInstance().unsetValue(LAYOUT_PROPERTY);
        } catch (Throwable e) {
            addSuppressedException(e);
        } finally {
            super.tearDown();
        }
    }

    public void testLayoutButtonsStayInTheEditorToolbar() {
        assertFalse(editor.isShowActionsInTabs());
    }

    public void testTheToolbarShowsTheLayoutsThenTheViews() {
        assertEquals(List.of("Editor only", "Editor and preview", "Preview only", "|",
                "Query", "Schema", "Lineage", "Test"), rightToolbarTexts());
    }

    public void testToolbarButtonsUseThePrototypeIcons() {
        assertEquals(List.of(DataformIcons.LAYOUT_EDITOR_ONLY, DataformIcons.LAYOUT_EDITOR_AND_PREVIEW,
                        DataformIcons.LAYOUT_PREVIEW_ONLY, DataformIcons.PREVIEW_QUERY, DataformIcons.PREVIEW_SCHEMA,
                        DataformIcons.LINEAGE, DataformIcons.PREVIEW_TEST),
                rightToolbarActions().stream().map(a -> a.getTemplatePresentation().getIcon()).toList());
    }

    public void testViewButtonsShowTheirText() {
        for (String text : List.of("Query", "Schema", "Lineage", "Test")) {
            assertEquals(text, Boolean.TRUE,
                    action(text).getTemplatePresentation().getClientProperty(ActionUtil.SHOW_TEXT_IN_TOOLBAR));
        }
    }

    public void testALayoutButtonSwitchesTheLayout() {
        editor.setLayout(Layout.SHOW_EDITOR);

        ((ToggleAction) action("Preview only")).setSelected(event(action("Preview only")), true);

        assertEquals(Layout.SHOW_PREVIEW, editor.getLayout());
        assertFalse(selected("Editor only"));
        assertFalse(selected("Editor and preview"));
        assertTrue(selected("Preview only"));
    }

    public void testAViewButtonOpensItsViewNextToTheEditor() {
        editor.setLayout(Layout.SHOW_EDITOR);

        click("Schema");

        assertEquals(Layout.SHOW_EDITOR_AND_PREVIEW, editor.getLayout());
        assertEquals(View.SCHEMA, preview.getActiveView());
        assertTrue(selected("Schema"));
        assertFalse(selected("Lineage"));
    }

    public void testClickingTheShownViewAgainHidesThePreview() {
        editor.setLayout(Layout.SHOW_EDITOR);
        click("Schema");

        click("Schema");

        assertEquals(Layout.SHOW_EDITOR, editor.getLayout());
        assertFalse(selected("Schema"));
    }

    public void testSwitchingViewsKeepsPreviewOnly() {
        editor.setLayout(Layout.SHOW_PREVIEW);
        preview.showPanel(View.LINEAGE);

        click("Schema");

        assertEquals(Layout.SHOW_PREVIEW, editor.getLayout());
        assertEquals(View.SCHEMA, preview.getActiveView());
        assertTrue(selected("Schema"));
    }

    public void testTheShownViewIsSelectedInPreviewOnly() {
        editor.setLayout(Layout.SHOW_PREVIEW);
        preview.showPanel(View.LINEAGE);

        assertTrue(selected("Lineage"));
        assertFalse(selected("Schema"));
    }

    public void testTheLeftToolbarShowsCompileAndRunQueryThenRunAction() {
        assertEquals(List.of("Compile", "Run query", "|", "Run action"), leftToolbarTexts());
    }

    public void testLeftToolbarButtonsUseThePrototypeIcons() {
        assertEquals(List.of(DataformIcons.COMPILE, DataformIcons.RUN, DataformIcons.RUN),
                leftToolbarActions().stream().map(a -> a.getTemplatePresentation().getIcon()).toList());
    }

    public void testOnlyRunActionShowsItsText() {
        assertEquals(List.of(false, false, true), leftToolbarActions().stream()
                .map(a -> a.getTemplatePresentation().getClientProperty(ActionUtil.SHOW_TEXT_IN_TOOLBAR) == Boolean.TRUE)
                .toList());
    }

    public void testRunQueryKeepsItsTextOnAnActionFile() {
        AnAction runQuery = leftToolbarActions().get(1);
        AnActionEvent e = event(runQuery);

        runQuery.update(e);

        assertEquals("Run query", e.getPresentation().getText());
    }

    public void testOpeningAnEditorWhileABuildRunsKeepsTheBuildRunning() {
        DataformBuildContext build = new DataformBuildContext(getProject());
        assertTrue(build.waitAndStart());
        try {
            editor.createLeftToolbarActionGroup();

            assertTrue(DataformBuildContext.isBuildRunning(getProject()));
        } finally {
            build.finished(true, "Build finished");
        }
    }

    public void testAnActionFileHidesTheTestView() {
        AnActionEvent e = event(action("Test"));
        action("Test").update(e);

        assertFalse(e.getPresentation().isVisible());
    }

    private void click(String text) {
        AnAction action = action(text);
        action.actionPerformed(event(action));
    }

    private boolean selected(String text) {
        AnAction action = action(text);
        AnActionEvent e = event(action);
        action.update(e);
        return Toggleable.isSelected(e.getPresentation());
    }

    private AnActionEvent event(AnAction action) {
        return TestActionEvent.createTestEvent(action);
    }

    private AnAction action(String text) {
        return rightToolbarActions().stream()
                .filter(a -> text.equals(a.getTemplatePresentation().getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No toolbar action " + text + " in " + rightToolbarTexts()));
    }

    private List<AnAction> rightToolbarActions() {
        return flatten(editor.createRightToolbar().getActionGroup()).stream()
                .filter(a -> !(a instanceof Separator))
                .toList();
    }

    private List<String> rightToolbarTexts() {
        return flatten(editor.createRightToolbar().getActionGroup()).stream()
                .map(a -> a instanceof Separator ? "|" : a.getTemplatePresentation().getText())
                .toList();
    }

    private List<AnAction> leftToolbarActions() {
        return flatten(editor.createLeftToolbarActionGroup()).stream()
                .filter(a -> !(a instanceof Separator))
                .toList();
    }

    private List<String> leftToolbarTexts() {
        return flatten(editor.createLeftToolbarActionGroup()).stream()
                .map(a -> a instanceof Separator ? "|" : a.getTemplatePresentation().getText())
                .toList();
    }

    private static List<AnAction> flatten(ActionGroup group) {
        List<AnAction> out = new ArrayList<>();
        for (AnAction child : ((DefaultActionGroup) group).getChildActionsOrStubs()) {
            if (child instanceof DefaultActionGroup nested) out.addAll(flatten(nested));
            else out.add(child);
        }
        return out;
    }
}
