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
package io.github.rejeb.dataform.language.completion;

import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;
import org.jetbrains.annotations.NotNull;

/**
 * Gives the lookup of a column its presentation: type, column icon and description.
 */
public final class ColumnLookups {

    private ColumnLookups() {
    }

    /**
     * Adds to {@code element} the type {@code typeName}, suffixed with {@code []} when the column is
     * repeated, the column icon, and the description of the column as a grayed tail when it has one.
     */
    public static @NotNull LookupElementBuilder describe(@NotNull LookupElementBuilder element,
                                                         @NotNull ColumnInfo column,
                                                         @NotNull String typeName) {
        LookupElementBuilder described = element
                .withTypeText(column.isRepeated() ? typeName + "[]" : typeName)
                .withIcon(AllIcons.Nodes.Field);
        String description = column.description();
        return description == null || description.isBlank()
                ? described
                : described.withTailText(" " + description, true);
    }
}
