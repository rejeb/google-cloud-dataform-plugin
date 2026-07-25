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
package io.github.rejeb.dataform.language.completion.config;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiDocumentManager;
import org.jetbrains.annotations.NotNull;

/**
 * Helpers writing completion results into the SQLX host document, since the config
 * block is edited through an injected JavaScript fragment.
 */
final class ConfigInsertion {

    private ConfigInsertion() {
    }

    static Editor hostEditor(@NotNull InsertionContext context) {
        return context.getEditor() instanceof EditorWindow window
                ? window.getDelegate()
                : context.getEditor();
    }

    static int hostOffset(@NotNull InsertionContext context, int injectedOffset) {
        return InjectedLanguageManager.getInstance(context.getProject())
                .injectedToHost(context.getFile(), injectedOffset);
    }

    static void applyText(@NotNull InsertionContext context,
                          @NotNull Editor editor,
                          int offset,
                          @NotNull ConfigValueSkeletonBuilder.Skeleton skeleton,
                          @NotNull String suffix) {
        Document document = editor.getDocument();
        document.insertString(offset, skeleton.text() + suffix);
        PsiDocumentManager.getInstance(context.getProject()).commitDocument(document);

        editor.getCaretModel().moveToOffset(offset + skeleton.caretOffset());
        if (skeleton.autoPopup()) {
            AutoPopupController.getInstance(context.getProject()).scheduleAutoPopup(editor);
        }
    }

    static boolean needsComma(@NotNull Document document, int offset) {
        CharSequence text = document.getCharsSequence();
        for (int i = offset; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ' || c == '\t') {
                continue;
            }
            return c != ',';
        }
        return true;
    }

    static String lineIndent(@NotNull Document document, int offset) {
        CharSequence text = document.getCharsSequence();
        int lineStart = document.getLineStartOffset(document.getLineNumber(offset));
        int end = lineStart;
        while (end < text.length() && (text.charAt(end) == ' ' || text.charAt(end) == '\t')) {
            end++;
        }
        return text.subSequence(lineStart, end).toString();
    }
}
