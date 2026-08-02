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

import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NotNull;

/**
 * Opens a column entry of the {@code columns} map on the description it takes, leaving the caret
 * inside the empty description of the column just picked.
 */
public class ConfigColumnEntryInsertHandler implements InsertHandler<LookupElement> {

    private static final String DESCRIPTION_SKELETON = ": \"\"";
    private static final int CARET_IN_DESCRIPTION = DESCRIPTION_SKELETON.length() - 1;

    @Override
    public void handleInsert(@NotNull InsertionContext context, @NotNull LookupElement item) {
        Editor editor = ConfigInsertion.hostEditor(context);
        Document document = editor.getDocument();
        int offset = ConfigInsertion.hostOffset(context, context.getTailOffset());

        if (ConfigInsertion.nextNonBlank(document, offset) == ':') {
            return;
        }
        ConfigInsertion.applyText(context, editor, offset,
                new ConfigValueSkeletonBuilder.Skeleton(
                        DESCRIPTION_SKELETON, CARET_IN_DESCRIPTION, false),
                ConfigInsertion.needsComma(document, offset) ? "," : "");
    }
}
