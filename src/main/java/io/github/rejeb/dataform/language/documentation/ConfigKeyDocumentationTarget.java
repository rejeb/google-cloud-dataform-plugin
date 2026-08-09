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
import com.intellij.platform.backend.documentation.DocumentationResult;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.presentation.TargetPresentation;
import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Shows what a SQLX config block key expects: the description carried by the generated schema, how
 * the key is filled in, and the columns of the action when the key names one.
 */
public class ConfigKeyDocumentationTarget implements DocumentationTarget {

    private final String key;
    private final String type;
    private final String description;
    private final String usage;
    private final List<ColumnInfo> availableColumns;

    public ConfigKeyDocumentationTarget(@NotNull String key,
                                        @Nullable String type,
                                        @Nullable String description,
                                        @Nullable String usage,
                                        @NotNull List<ColumnInfo> availableColumns) {
        this.key = key;
        this.type = type;
        this.description = description;
        this.usage = usage;
        this.availableColumns = availableColumns;
    }

    @Override
    public @NotNull Pointer<? extends DocumentationTarget> createPointer() {
        return Pointer.hardPointer(this);
    }

    @Override
    public @NotNull TargetPresentation computePresentation() {
        return TargetPresentation.builder(key).icon(AllIcons.Nodes.Property).presentation();
    }

    @Override
    public @NotNull DocumentationResult computeDocumentation() {
        return DocumentationResult.documentation(html());
    }

    /**
     * The documentation content of this key, as handed to the popup.
     */
    public @NotNull String html() {
        return DataformDocumentationRenderer.renderConfigKey(
                key, type, description, usage, availableColumns);
    }
}
