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

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NotNull;

/**
 * Replaces the picked shape name with the value skeleton of that shape, for
 * properties accepting several shapes.
 */
public class ConfigValueVariantInsertHandler implements InsertHandler<LookupElement> {

    private final ConfigValueSkeletonBuilder skeletonBuilder;
    private final ObjectNode variantSchema;

    public ConfigValueVariantInsertHandler(@NotNull ConfigSchemaLookup lookup,
                                           @NotNull ObjectNode variantSchema) {
        this.skeletonBuilder = new ConfigValueSkeletonBuilder(lookup);
        this.variantSchema = variantSchema;
    }

    @Override
    public void handleInsert(@NotNull InsertionContext context, @NotNull LookupElement item) {
        Editor editor = ConfigInsertion.hostEditor(context);
        Document document = editor.getDocument();
        int start = ConfigInsertion.hostOffset(context, context.getStartOffset());
        int end = ConfigInsertion.hostOffset(context, context.getTailOffset());

        document.deleteString(start, end);

        ConfigValueSkeletonBuilder.Skeleton skeleton = skeletonBuilder
                .buildValue(variantSchema, ConfigInsertion.lineIndent(document, start));

        ConfigInsertion.applyText(context, editor, start, skeleton,
                ConfigInsertion.needsComma(document, start) ? "," : "");
    }
}
