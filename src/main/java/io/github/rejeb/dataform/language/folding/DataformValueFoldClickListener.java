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
package io.github.rejeb.dataform.language.folding;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.CustomFoldRegion;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseEventArea;
import com.intellij.openapi.editor.event.EditorMouseListener;
import com.intellij.openapi.editor.event.EditorMouseMotionListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Cursor;
import java.awt.event.MouseEvent;

/**
 * Brings the expression source back when the user clicks the value painted over several lines.
 *
 * <p>A multi-line value lives in a custom fold region, which the platform never expands on click,
 * so it is made clickable here and shown with a hand cursor.</p>
 */
public final class DataformValueFoldClickListener
        implements EditorFactoryListener, EditorMouseListener, EditorMouseMotionListener {

    private static final Key<Boolean> ATTACHED = Key.create("dataform.folding.clickListener");
    private static final Key<Boolean> HAND_CURSOR = Key.create("dataform.folding.handCursor");

    private static final DataformValueFoldClickListener INSTANCE =
            new DataformValueFoldClickListener();

    /**
     * Makes the values of the editors already open clickable. Editors opened before the plugin was
     * loaded never see {@link #editorCreated(EditorFactoryEvent)}, so a project opening sweeps them.
     */
    public static void attachToOpenEditors() {
        ApplicationManager.getApplication().invokeLater(() -> {
            for (Editor editor : EditorFactory.getInstance().getAllEditors()) {
                attach(editor);
            }
        });
    }

    /**
     * Tells whether the values of the given editor are already clickable. Used by the tests to
     * check that the platform really calls this listener for a newly opened editor.
     */
    public static boolean isAttachedTo(@NotNull Editor editor) {
        return editor.getUserData(ATTACHED) != null;
    }

    @Override
    public void editorCreated(@NotNull EditorFactoryEvent event) {
        attach(event.getEditor());
    }

    @Override
    public void editorReleased(@NotNull EditorFactoryEvent event) {
        Editor editor = event.getEditor();
        if (editor.getUserData(ATTACHED) == null) {
            return;
        }
        editor.putUserData(ATTACHED, null);
        editor.removeEditorMouseListener(INSTANCE);
        editor.removeEditorMouseMotionListener(INSTANCE);
    }

    @Override
    public void mouseClicked(@NotNull EditorMouseEvent event) {
        if (event.isConsumed()
                || event.getMouseEvent().getButton() != MouseEvent.BUTTON1
                || event.getMouseEvent().getClickCount() != 1) {
            return;
        }
        DataformValueFoldRenderer renderer = rendererAt(event);
        if (renderer == null) {
            return;
        }
        setHandCursor(event.getEditor(), false);
        renderer.showSource();
        event.consume();
    }

    @Override
    public void mouseMoved(@NotNull EditorMouseEvent event) {
        setHandCursor(event.getEditor(), rendererAt(event) != null);
    }

    private static void attach(@NotNull Editor editor) {
        if (editor.isDisposed() || editor.getUserData(ATTACHED) != null) {
            return;
        }
        editor.putUserData(ATTACHED, Boolean.TRUE);
        editor.addEditorMouseListener(INSTANCE);
        editor.addEditorMouseMotionListener(INSTANCE);
    }

    private static @Nullable DataformValueFoldRenderer rendererAt(@NotNull EditorMouseEvent event) {
        if (event.getArea() != EditorMouseEventArea.EDITING_AREA) {
            return null;
        }
        FoldRegion region = event.getCollapsedFoldRegion();
        if (!(region instanceof CustomFoldRegion custom)) {
            return null;
        }
        return custom.getRenderer() instanceof DataformValueFoldRenderer renderer ? renderer : null;
    }

    private static void setHandCursor(@NotNull Editor editor, boolean wanted) {
        if (!(editor instanceof EditorEx editorEx) || editor.isDisposed()) {
            return;
        }
        if (wanted == Boolean.TRUE.equals(editor.getUserData(HAND_CURSOR))) {
            return;
        }
        editor.putUserData(HAND_CURSOR, wanted ? Boolean.TRUE : null);
        editorEx.setCustomCursor(DataformValueFoldClickListener.class,
                wanted ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : null);
    }
}
