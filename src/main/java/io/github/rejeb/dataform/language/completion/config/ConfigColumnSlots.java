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

import com.intellij.lang.javascript.psi.JSObjectLiteralExpression;
import com.intellij.lang.javascript.psi.JSProperty;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Recognises the places of a SQLX config block that name a column of the action output.
 *
 * <p>Recognition is done on property names rather than on the generated JSON schema: the schema
 * describes the shape a value takes, not whether the string it holds is a column name.</p>
 */
public final class ConfigColumnSlots {

    private static final String COLUMNS = "columns";
    private static final String PARTITION_BY = "partitionBy";
    private static final String UNIQUE_KEY = "uniqueKey";
    private static final String FIELD = "field";
    private static final String ASSERTIONS = "assertions";

    private static final Set<String> COLUMN_NAME_KEYS =
            Set.of("partitionBy", "clusterBy", "uniqueKey", "uniqueKeys", "nonNull");

    private ConfigColumnSlots() {
    }

    /**
     * What a column-naming place expects.
     */
    public enum Kind {
        /** A key of a {@code columns} map, which is the name of a described column. */
        ENTRY_KEY,
        /** A string value naming a column, as in {@code clusterBy} or {@code assertions.nonNull}. */
        NAME_VALUE
    }

    /**
     * A column-naming place, together with the record columns to walk through before the names it
     * accepts are reached. The path is empty everywhere but in a {@code columns} map nested in a
     * record column descriptor.
     */
    public record Slot(@NotNull Kind kind, @NotNull List<String> recordPath) {
    }

    /**
     * Returns the column-naming place the position sits in, empty when it names no column.
     */
    public static Optional<Slot> at(@NotNull PsiElement position) {
        if (!ConfigSchemaLookup.isInConfigBlock(position)) {
            return Optional.empty();
        }
        JSProperty property = PsiTreeUtil.getParentOfType(position, JSProperty.class, false);
        if (property != null && ConfigSchemaLookup.isValuePosition(property, position)) {
            return isColumnNameKey(property)
                    ? Optional.of(new Slot(Kind.NAME_VALUE, List.of()))
                    : Optional.empty();
        }
        JSObjectLiteralExpression map =
                PsiTreeUtil.getParentOfType(position, JSObjectLiteralExpression.class, false);
        return isColumnsMap(map)
                ? Optional.of(new Slot(Kind.ENTRY_KEY, recordPathOfMap(map)))
                : Optional.empty();
    }

    /**
     * Tells whether the given property declares a column of a {@code columns} map, which makes its
     * name a column name.
     */
    public static boolean isColumnEntry(@NotNull JSProperty property) {
        return isColumnsMap(PsiTreeUtil.getParentOfType(
                property, JSObjectLiteralExpression.class, true));
    }

    /**
     * Tells whether the given property is one the config fills with column names.
     */
    public static boolean isColumnNameKey(@NotNull JSProperty property) {
        String name = property.getName();
        if (name == null) {
            return false;
        }
        return COLUMN_NAME_KEYS.contains(name)
                || (FIELD.equals(name) && isDeclaredUnder(property, PARTITION_BY));
    }

    /**
     * Tells whether the given property is the {@code uniqueKey} of the assertions block rather than
     * the one driving an incremental merge, the two carrying a different meaning.
     */
    public static boolean isAssertionKey(@NotNull JSProperty property) {
        return UNIQUE_KEY.equals(property.getName()) && isDeclaredUnder(property, ASSERTIONS);
    }

    /**
     * Returns the record columns leading to the {@code columns} map the given property declares.
     */
    @NotNull
    public static List<String> recordPathOfColumnsKey(@NotNull JSProperty columnsProperty) {
        return pathFromDeclaringObject(PsiTreeUtil.getParentOfType(
                columnsProperty, JSObjectLiteralExpression.class, true));
    }

    /**
     * Returns the record columns leading to the {@code columns} map the given column entry belongs
     * to, empty when the map is the one of the action itself.
     */
    @NotNull
    public static List<String> recordPathOfEntry(@NotNull JSProperty columnEntry) {
        return recordPathOfMap(PsiTreeUtil.getParentOfType(
                columnEntry, JSObjectLiteralExpression.class, true));
    }

    @NotNull
    private static List<String> recordPathOfMap(@Nullable JSObjectLiteralExpression columnsMap) {
        JSProperty columnsProperty = columnsMap == null
                ? null
                : PsiTreeUtil.getParentOfType(columnsMap, JSProperty.class, true);
        return columnsProperty == null ? List.of() : recordPathOfColumnsKey(columnsProperty);
    }

    private static boolean isColumnsMap(@Nullable JSObjectLiteralExpression object) {
        if (object == null) {
            return false;
        }
        JSProperty owner = PsiTreeUtil.getParentOfType(object, JSProperty.class, true);
        return owner != null && COLUMNS.equals(owner.getName()) && owner.getValue() == object;
    }

    /**
     * Walks up from the object declaring a {@code columns} map to the config root, collecting the
     * record columns the map is nested in. The map of the action itself yields an empty path.
     */
    @NotNull
    private static List<String> pathFromDeclaringObject(@Nullable JSObjectLiteralExpression object) {
        LinkedList<String> path = new LinkedList<>();
        JSObjectLiteralExpression current = object;
        while (current != null) {
            JSProperty column = PsiTreeUtil.getParentOfType(current, JSProperty.class, true);
            if (column == null || column.getName() == null) {
                return path;
            }
            path.addFirst(column.getName());
            JSObjectLiteralExpression map =
                    PsiTreeUtil.getParentOfType(column, JSObjectLiteralExpression.class, true);
            JSProperty columnsProperty = map == null
                    ? null
                    : PsiTreeUtil.getParentOfType(map, JSProperty.class, true);
            current = columnsProperty == null
                    ? null
                    : PsiTreeUtil.getParentOfType(columnsProperty, JSObjectLiteralExpression.class, true);
        }
        return path;
    }

    private static boolean isDeclaredUnder(@NotNull JSProperty property, @NotNull String parentName) {
        JSObjectLiteralExpression owner =
                PsiTreeUtil.getParentOfType(property, JSObjectLiteralExpression.class, true);
        JSProperty parent = owner == null
                ? null
                : PsiTreeUtil.getParentOfType(owner, JSProperty.class, true);
        return parent != null && parentName.equals(parent.getName());
    }
}
