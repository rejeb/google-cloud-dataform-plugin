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
package io.github.rejeb.dataform.language.config;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiDocumentManager;
import io.github.rejeb.dataform.language.util.SqlxEditors;
import org.jetbrains.annotations.NotNull;

/**
 * Helpers writing completion results into the SQLX host document, since the config
 * block is edited through an injected JavaScript fragment.
 */
public final class ConfigInsertion {

    private ConfigInsertion() {
    }

    /**
     * The editor over the SQLX file, unwrapping the one over the injected config fragment.
     */
    public static Editor hostEditor(@NotNull InsertionContext context) {
        return SqlxEditors.host(context.getEditor());
    }

    /**
     * Translates an offset of the injected config fragment into one of the SQLX file.
     */
    public static int hostOffset(@NotNull InsertionContext context, int injectedOffset) {
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
        return nextNonBlank(document, offset) != ',';
    }

    /**
     * The first character after the offset that is neither a space nor a tab, {@code '\0'} when the
     * line, and everything after it, holds none.
     */
    static char nextNonBlank(@NotNull Document document, int offset) {
        int index = nextNonBlankOffset(document, offset);
        return index < document.getTextLength() ? document.getCharsSequence().charAt(index) : '\0';
    }

    /**
     * The offset of the first character after the given one that is neither a space nor a tab, the
     * document length when there is none.
     */
    static int nextNonBlankOffset(@NotNull Document document, int offset) {
        CharSequence text = document.getCharsSequence();
        int i = offset;
        while (i < text.length() && (text.charAt(i) == ' ' || text.charAt(i) == '\t')) {
            i++;
        }
        return i;
    }

    /**
     * The offset where the value already written after the property name ending at the given offset
     * starts, {@code -1} when no colon follows the name or no value follows the colon on its line.
     */
    static int existingValueOffset(@NotNull Document document, int offset) {
        if (nextNonBlank(document, offset) != ':') {
            return -1;
        }
        int valueStart = nextNonBlankOffset(document, nextNonBlankOffset(document, offset) + 1);
        char c = nextNonBlank(document, valueStart);
        return c == '\0' || c == '\n' || c == '\r' || c == ',' || c == '}' || c == ']'
                ? -1
                : valueStart;
    }

    /**
     * The offset right after the colon, and the blanks following it, written after the property name
     * ending at the given offset; the offset itself when no colon follows the name.
     */
    static int afterExistingColon(@NotNull Document document, int offset) {
        int colon = nextNonBlankOffset(document, offset);
        if (nextNonBlank(document, offset) != ':') {
            return offset;
        }
        return nextNonBlankOffset(document, colon + 1);
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
