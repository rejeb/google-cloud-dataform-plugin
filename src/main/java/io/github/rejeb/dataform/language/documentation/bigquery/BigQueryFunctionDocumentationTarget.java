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
package io.github.rejeb.dataform.language.documentation.bigquery;

import com.intellij.icons.AllIcons;
import com.intellij.model.Pointer;
import com.intellij.platform.backend.documentation.DocumentationResult;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.presentation.TargetPresentation;
import io.github.rejeb.dataform.language.documentation.DataformDocumentationRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BigQueryFunctionDocumentationTarget implements DocumentationTarget {

    private final BigQueryFunctionDoc myDoc;

    public BigQueryFunctionDocumentationTarget(@NotNull BigQueryFunctionDoc doc) {
        this.myDoc = doc;
    }

    @Override
    public @NotNull Pointer<? extends DocumentationTarget> createPointer() {
        return Pointer.hardPointer(this);
    }

    @Override
    public @NotNull TargetPresentation computePresentation() {
        return TargetPresentation.builder(myDoc.name())
                .icon(AllIcons.Nodes.Function)
                .locationText(myDoc.category())
                .presentation();
    }

    @Override
    public @Nullable DocumentationResult computeDocumentation() {
        return DocumentationResult.documentation(
                DataformDocumentationRenderer.renderFunction(myDoc));
    }
}
