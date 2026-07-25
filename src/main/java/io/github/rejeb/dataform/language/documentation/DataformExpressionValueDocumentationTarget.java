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
package io.github.rejeb.dataform.language.documentation;

import com.intellij.icons.AllIcons;
import com.intellij.model.Pointer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.platform.backend.documentation.DocumentationResult;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.presentation.TargetPresentation;
import io.github.rejeb.dataform.language.folding.DataformFoldingPlaceholder;
import org.jetbrains.annotations.NotNull;

/**
 * Shows the evaluated value of a Dataform expression, formatted over as many lines as it needs.
 *
 * <p>A folded region can only display a single line, so the full value is offered here.</p>
 */
public class DataformExpressionValueDocumentationTarget implements DocumentationTarget {

    private final String source;
    private final String value;

    public DataformExpressionValueDocumentationTarget(@NotNull String source, @NotNull String value) {
        this.source = source;
        this.value = value;
    }

    @Override
    public @NotNull Pointer<? extends DocumentationTarget> createPointer() {
        return Pointer.hardPointer(this);
    }

    @Override
    public @NotNull TargetPresentation computePresentation() {
        return TargetPresentation.builder(StringUtil.shortenTextWithEllipsis(source.replace('\n', ' '), 80, 0))
                .icon(AllIcons.Nodes.Variable)
                .presentation();
    }

    @Override
    public @NotNull DocumentationResult computeDocumentation() {
        String html = "<div class='definition'><pre>"
                + StringUtil.escapeXmlEntities(source.strip())
                + "</pre></div><div class='content'><pre>"
                + StringUtil.escapeXmlEntities(DataformFoldingPlaceholder.expanded(value))
                + "</pre></div>";
        return DocumentationResult.documentation(html);
    }
}
