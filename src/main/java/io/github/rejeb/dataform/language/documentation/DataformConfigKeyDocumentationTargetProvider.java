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

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.javascript.psi.JSProperty;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.documentation.DocumentationTargetProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import io.github.rejeb.dataform.language.completion.config.ConfigColumnSlots;
import io.github.rejeb.dataform.language.completion.config.ConfigSchemaLookup;
import io.github.rejeb.dataform.language.schema.sql.DataformActionColumns;
import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;
import io.github.rejeb.dataform.language.schema.sql.model.DataformDasTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Offers, as quick documentation of a SQLX config block key, what the key expects: how it is
 * filled in and, when it names a column, the columns the action actually produces.
 */
public class DataformConfigKeyDocumentationTargetProvider implements DocumentationTargetProvider {

    private static final String COLUMNS = "columns";

    @Override
    public @NotNull List<? extends DocumentationTarget> documentationTargets(@NotNull PsiFile file,
                                                                             int offset) {
        JSProperty property = keyPropertyAt(file, offset);
        if (property == null || property.getName() == null
                || !ConfigSchemaLookup.isInConfigBlock(property)) {
            return List.of();
        }
        return List.of(ConfigColumnSlots.isColumnEntry(property)
                ? columnEntryTarget(property, property.getName())
                : configKeyTarget(property, property.getName()));
    }

    /**
     * Documents a column entry of a {@code columns} map with the column it describes, falling back
     * to how such an entry is written when the column is unknown to the extracted schema.
     */
    @NotNull
    private static DocumentationTarget columnEntryTarget(@NotNull JSProperty property,
                                                         @NotNull String name) {
        Optional<DataformDasTable> table = DataformActionColumns.tableAt(property);
        List<ColumnInfo> siblings = DataformActionColumns.descend(
                table.map(DataformDasTable::getColumns).orElseGet(List::of),
                ConfigColumnSlots.recordPathOfEntry(property));
        Optional<ColumnInfo> column = DataformActionColumns.find(siblings, name);
        if (column.isPresent()) {
            return new DataformColumnDocumentationTarget(column.get(),
                    table.map(DataformDasTable::getName).orElse(null));
        }
        return new ConfigKeyDocumentationTarget(name, null, null,
                ConfigKeyUsage.columnEntry(), siblings);
    }

    @NotNull
    private static DocumentationTarget configKeyTarget(@NotNull JSProperty property,
                                                       @NotNull String name) {
        ConfigSchemaLookup lookup = ConfigSchemaLookup.create(property).orElse(null);
        ObjectNode schema = lookup == null ? null : lookup.schemaOf(property).orElse(null);
        return new ConfigKeyDocumentationTarget(
                name,
                schema == null ? null : lookup.typeText(schema),
                schema == null ? null : schema.path("description").asText(null),
                ConfigKeyUsage.of(name, ConfigColumnSlots.isAssertionKey(property)),
                availableColumns(property, name));
    }

    /**
     * The columns a key can name: those of the action for a column-naming key, those reachable
     * under the {@code columns} map the key declares, and none for any other key.
     */
    @NotNull
    private static List<ColumnInfo> availableColumns(@NotNull JSProperty property,
                                                     @NotNull String name) {
        if (COLUMNS.equals(name)) {
            return DataformActionColumns.descend(DataformActionColumns.at(property),
                    ConfigColumnSlots.recordPathOfColumnsKey(property));
        }
        return ConfigColumnSlots.isColumnNameKey(property)
                ? DataformActionColumns.at(property)
                : List.of();
    }

    /**
     * The config property whose key is read at this offset, {@code null} when the offset reads a
     * value or no property at all. The character before the offset is considered too, so that a
     * caret sitting right after a key still documents it.
     */
    @Nullable
    private static JSProperty keyPropertyAt(@NotNull PsiFile file, int offset) {
        JSProperty property = keyPropertyOf(elementAt(file, offset));
        return property != null || offset == 0 ? property : keyPropertyOf(elementAt(file, offset - 1));
    }

    @Nullable
    private static JSProperty keyPropertyOf(@Nullable PsiElement element) {
        if (element == null) {
            return null;
        }
        JSProperty property = PsiTreeUtil.getParentOfType(element, JSProperty.class, false);
        return property != null && !ConfigSchemaLookup.isValuePosition(property, element)
                ? property
                : null;
    }

    @Nullable
    private static PsiElement elementAt(@NotNull PsiFile file, int offset) {
        PsiElement injected = InjectedLanguageManager.getInstance(file.getProject())
                .findInjectedElementAt(file, offset);
        return injected != null ? injected : file.findElementAt(offset);
    }
}
