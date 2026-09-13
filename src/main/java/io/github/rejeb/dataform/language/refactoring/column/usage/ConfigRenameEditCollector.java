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
package io.github.rejeb.dataform.language.refactoring.column.usage;

import com.intellij.lang.javascript.psi.JSLiteralExpression;
import com.intellij.lang.javascript.psi.JSProperty;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import io.github.rejeb.dataform.language.completion.config.ConfigColumnSlots;
import io.github.rejeb.dataform.language.injection.InjectedFiles;
import io.github.rejeb.dataform.language.psi.SqlxConfigBlock;
import io.github.rejeb.dataform.language.refactoring.column.DataformColumnNameValidator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects the places of a config block that name a column.
 *
 * <p>The config of an action describes the columns that action produces, so the block of the file
 * declaring a column is the only one that can name it. Recognition is delegated to
 * {@link ConfigColumnSlots}, which is what completion already uses, so the two stay in step.</p>
 */
public final class ConfigRenameEditCollector {

    private static final String PARTITION_BY = "partitionBy";
    private static final String ROW_CONDITIONS = "rowConditions";

    private ConfigRenameEditCollector() {
    }

    /**
     * The config places of {@code hostFile} naming {@code oldName}.
     */
    public static @NotNull List<ColumnRenameEdit> collect(@NotNull PsiFile hostFile,
                                                          @NotNull String oldName,
                                                          @NotNull String newName) {
        List<ColumnRenameEdit> edits = new ArrayList<>();
        for (PsiFile injected : InjectedFiles.inside(hostFile, SqlxConfigBlock.class)) {
            for (JSProperty property : PsiTreeUtil.findChildrenOfType(injected, JSProperty.class)) {
                collectEntryKey(property, oldName, newName, edits);
                collectNameValues(property, oldName, newName, edits);
                collectExpressions(property, oldName, newName, edits);
            }
        }
        return edits;
    }

    /** A key of the {@code columns} map, which is the name of a described column. */
    private static void collectEntryKey(@NotNull JSProperty property,
                                        @NotNull String oldName,
                                        @NotNull String newName,
                                        @NotNull List<ColumnRenameEdit> edits) {
        if (!ConfigColumnSlots.isColumnEntry(property)) return;
        String name = property.getName();
        if (name == null || !name.equalsIgnoreCase(oldName)) return;
        PsiElement identifier = property.getNameIdentifier();
        if (identifier == null) return;
        add(edits, EditFactory.ofWhole(identifier, DataformColumnNameValidator.asConfigKey(newName),
                ColumnRenameEdit.Kind.CONFIG_COLUMN_KEY, ColumnRenameEdit.Risk.CERTAIN,
                "described column " + oldName));
    }

    /** A string value naming a column, as in {@code clusterBy} or {@code assertions.nonNull}. */
    private static void collectNameValues(@NotNull JSProperty property,
                                          @NotNull String oldName,
                                          @NotNull String newName,
                                          @NotNull List<ColumnRenameEdit> edits) {
        if (!ConfigColumnSlots.isColumnNameKey(property)) return;
        PsiElement value = property.getValue();
        if (value == null) return;
        for (JSLiteralExpression literal : stringLiterals(value)) {
            if (!oldName.equalsIgnoreCase(stringValueOf(literal))) continue;
            add(edits, EditFactory.ofLiteral(literal, newName,
                    ColumnRenameEdit.Kind.CONFIG_COLUMN_NAME, ColumnRenameEdit.Risk.CERTAIN,
                    property.getName() + " of the config"));
        }
    }

    /**
     * A column named inside a string that holds an expression rather than a bare name: the string
     * form of {@code partitionBy}, and the row conditions of the assertions block.
     *
     * <p>The object form of {@code partitionBy} names its column in {@code field}, which is a bare
     * name and is collected as one; only the string form holds an expression.</p>
     *
     * <p>Both are found by matching the name in the text of the string, as a whole identifier. A
     * partitioning expression is over the columns of the action itself and BigQuery only partitions
     * on one of them, so a match there is the column and is written without asking. A row condition
     * is an arbitrary predicate the user reviews.</p>
     */
    private static void collectExpressions(@NotNull JSProperty property,
                                           @NotNull String oldName,
                                           @NotNull String newName,
                                           @NotNull List<ColumnRenameEdit> edits) {
        String name = property.getName();
        if (name == null) return;
        boolean partition = PARTITION_BY.equals(name);
        boolean rowConditions = ROW_CONDITIONS.equals(name);
        if (!partition && !rowConditions) return;
        PsiElement value = property.getValue();
        if (value == null) return;
        if (partition && !(value instanceof JSLiteralExpression)) {
            return;
        }

        for (JSLiteralExpression literal : stringLiterals(value)) {
            String text = literal.getText();
            for (TextRange range : IdentifierOccurrences.of(text, oldName)) {
                add(edits, EditFactory.of(literal, range, newName,
                        partition
                                ? ColumnRenameEdit.Kind.CONFIG_PARTITION_EXPRESSION
                                : ColumnRenameEdit.Kind.CONFIG_ROW_CONDITION,
                        partition ? ColumnRenameEdit.Risk.CERTAIN : ColumnRenameEdit.Risk.HEURISTIC,
                        name + " of the config"));
            }
        }
    }

    private static @NotNull List<JSLiteralExpression> stringLiterals(@NotNull PsiElement root) {
        List<JSLiteralExpression> literals = new ArrayList<>();
        if (root instanceof JSLiteralExpression literal && literal.isStringLiteral()) {
            literals.add(literal);
        }
        for (JSLiteralExpression literal : PsiTreeUtil.findChildrenOfType(root, JSLiteralExpression.class)) {
            if (literal.isStringLiteral()) literals.add(literal);
        }
        return literals;
    }

    private static @Nullable String stringValueOf(@NotNull JSLiteralExpression literal) {
        Object value = literal.getValue();
        return value instanceof String text ? text : null;
    }

    private static void add(@NotNull List<ColumnRenameEdit> edits, @Nullable ColumnRenameEdit edit) {
        if (edit != null) edits.add(edit);
    }
}
