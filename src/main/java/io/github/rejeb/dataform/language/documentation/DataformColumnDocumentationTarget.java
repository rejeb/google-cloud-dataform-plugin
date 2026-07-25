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
import com.intellij.platform.backend.presentation.TargetPresentationBuilder;
import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DataformColumnDocumentationTarget implements DocumentationTarget {

    private final ColumnInfo myColumn;
    private final String myTableName;

    public DataformColumnDocumentationTarget(@NotNull ColumnInfo column, @Nullable String tableName) {
        this.myColumn = column;
        this.myTableName = tableName;
    }

    @Override
    public @NotNull Pointer<? extends DocumentationTarget> createPointer() {
        return Pointer.hardPointer(this);
    }

    @Override
    public @NotNull TargetPresentation computePresentation() {
        TargetPresentationBuilder builder = TargetPresentation.builder(myColumn.name())
                .icon(AllIcons.Nodes.Field);
        return myTableName == null
                ? builder.presentation()
                : builder.locationText(myTableName).presentation();
    }

    @Override
    public @Nullable DocumentationResult computeDocumentation() {
        return DocumentationResult.documentation(
                DataformDocumentationRenderer.renderColumn(myColumn, myTableName));
    }
}
