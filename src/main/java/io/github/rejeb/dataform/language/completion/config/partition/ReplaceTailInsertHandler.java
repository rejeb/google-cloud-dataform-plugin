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
package io.github.rejeb.dataform.language.completion.config.partition;

import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiDocumentManager;
import io.github.rejeb.dataform.language.completion.config.ConfigInsertion;
import org.jetbrains.annotations.NotNull;

/**
 * Drops what the caret left behind of the value being replaced, so that picking a proposal in the
 * middle of an argument rewrites it whole rather than splicing into it.
 */
public class ReplaceTailInsertHandler implements InsertHandler<LookupElement> {

    private final int tailLength;

    public ReplaceTailInsertHandler(int tailLength) {
        this.tailLength = tailLength;
    }

    @Override
    public void handleInsert(@NotNull InsertionContext context, @NotNull LookupElement item) {
        if (tailLength <= 0) {
            return;
        }
        Editor editor = ConfigInsertion.hostEditor(context);
        Document document = editor.getDocument();
        int tail = ConfigInsertion.hostOffset(context, context.getTailOffset());
        document.deleteString(tail, Math.min(document.getTextLength(), tail + tailLength));
        PsiDocumentManager.getInstance(context.getProject()).commitDocument(document);
        editor.getCaretModel().moveToOffset(tail);
    }
}
