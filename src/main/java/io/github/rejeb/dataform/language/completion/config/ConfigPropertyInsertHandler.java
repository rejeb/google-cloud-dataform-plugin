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
 * Inserts a config property with a value skeleton matching its schema type.
 */
public class ConfigPropertyInsertHandler implements InsertHandler<LookupElement> {

    private final ConfigValueSkeletonBuilder skeletonBuilder;
    private final ObjectNode propertySchema;

    public ConfigPropertyInsertHandler(@NotNull ConfigSchemaLookup lookup,
                                       @NotNull ObjectNode propertySchema) {
        this.skeletonBuilder = new ConfigValueSkeletonBuilder(lookup);
        this.propertySchema = propertySchema;
    }

    @Override
    public void handleInsert(@NotNull InsertionContext context, @NotNull LookupElement item) {
        Editor editor = ConfigInsertion.hostEditor(context);
        Document document = editor.getDocument();
        int offset = ConfigInsertion.hostOffset(context, context.getTailOffset());

        ConfigValueSkeletonBuilder.Skeleton skeleton = skeletonBuilder
                .build(propertySchema, ConfigInsertion.lineIndent(document, offset));

        boolean comma = !ConfigValueSkeletonBuilder.isValuePending(skeleton)
                && ConfigInsertion.needsComma(document, offset);
        ConfigInsertion.applyText(context, editor, offset, skeleton, comma ? "," : "");
    }
}
