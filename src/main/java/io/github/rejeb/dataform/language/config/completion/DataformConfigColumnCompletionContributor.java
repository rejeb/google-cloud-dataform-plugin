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
package io.github.rejeb.dataform.language.config.completion;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.lang.javascript.psi.JSLiteralExpression;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import io.github.rejeb.dataform.language.config.ConfigColumnEntryInsertHandler;
import io.github.rejeb.dataform.language.config.ConfigColumnSlots;
import io.github.rejeb.dataform.language.config.ConfigSchemaLookup;
import io.github.rejeb.dataform.language.config.partition.PartitionExpressionCursor;
import io.github.rejeb.dataform.language.config.partition.PartitionFormLookups;
import io.github.rejeb.dataform.language.schema.sql.DataformActionColumns;
import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Completes the columns of the action a SQLX file declares wherever its config block names one:
 * the keys of the {@code columns} map and the column names taken by {@code clusterBy},
 * {@code uniqueKey} and the {@code assertions} block.
 *
 * <p>The string form of {@code partitionBy} holds an expression rather than a bare column name and
 * is completed with the partitioning forms BigQuery accepts for the types the action declares.</p>
 */
public class DataformConfigColumnCompletionContributor extends CompletionContributor {

    public DataformConfigColumnCompletionContributor() {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), new Provider());
    }

    private static final class Provider extends CompletionProvider<CompletionParameters> {

        @Override
        protected void addCompletions(@NotNull CompletionParameters parameters,
                                      @NotNull ProcessingContext context,
                                      @NotNull CompletionResultSet result) {
            PsiElement position = parameters.getPosition();
            Optional<ConfigColumnSlots.Slot> slot = ConfigColumnSlots.at(position);
            if (slot.isEmpty()) {
                return;
            }
            List<ColumnInfo> columns = DataformActionColumns.descend(
                    DataformActionColumns.knownOrInferred(parameters.getOriginalFile()), slot.get().recordPath());
            if (slot.get().kind() == ConfigColumnSlots.Kind.PARTITION_EXPRESSION) {
                addPartitionExpressions(columns, parameters, result);
                return;
            }
            if (columns.isEmpty()) {
                return;
            }
            Set<String> declared = slot.get().kind() == ConfigColumnSlots.Kind.ENTRY_KEY
                    ? ConfigSchemaLookup.siblingKeys(position)
                    : ConfigSchemaLookup.siblingStrings(position);
            boolean added = false;
            for (ColumnInfo column : columns) {
                if (declared.contains(column.name())) {
                    continue;
                }
                result.addElement(lookupElement(column, position, slot.get().kind()));
                added = true;
            }
            if (added) {
                result.stopHere();
            }
        }

        /**
         * Offers what the caret is editing of the partitioning expression: the expression itself,
         * the column the function it holds takes, or its truncation unit. Unlike a column name, an
         * expression is offered even when the schema is unknown, since its shape does not depend on
         * it.
         *
         * <p>The proposals are matched against the argument being edited rather than against the
         * whole string, which JavaScript takes as the prefix of a literal.</p>
         */
        private static void addPartitionExpressions(@NotNull List<ColumnInfo> columns,
                                                    @NotNull CompletionParameters parameters,
                                                    @NotNull CompletionResultSet result) {
            JSLiteralExpression literal = PsiTreeUtil.getParentOfType(
                    parameters.getPosition(), JSLiteralExpression.class, false);
            PartitionExpressionCursor cursor = literal == null
                    ? PartitionExpressionCursor.empty()
                    : PartitionExpressionCursor.inLiteral(literal.getText(),
                            parameters.getOffset() - literal.getTextRange().getStartOffset());

            CompletionResultSet sink = result.withPrefixMatcher(cursor.prefix());
            PartitionFormLookups.of(columns, cursor, literal != null).forEach(sink::addElement);
            result.stopHere();
        }

        @NotNull
        private static LookupElementBuilder lookupElement(@NotNull ColumnInfo column,
                                                          @NotNull PsiElement position,
                                                          @NotNull ConfigColumnSlots.Kind kind) {
            LookupElementBuilder element = LookupElementBuilder.create(insertedText(column, position, kind))
                    .withLookupString(column.name())
                    .withPresentableText(column.name())
                    .withTypeText(typeText(column))
                    .withIcon(AllIcons.Nodes.Field);
            if (column.description() != null && !column.description().isBlank()) {
                element = element.withTailText(" " + column.description(), true);
            }
            return kind == ConfigColumnSlots.Kind.ENTRY_KEY
                    ? element.withInsertHandler(new ConfigColumnEntryInsertHandler())
                    : element;
        }

        /**
         * The text a picked column writes at the caret. A map key is written bare unless the column
         * name is no valid JavaScript identifier, while a value is quoted unless the caret already
         * sits inside a string.
         */
        @NotNull
        private static String insertedText(@NotNull ColumnInfo column,
                                           @NotNull PsiElement position,
                                           @NotNull ConfigColumnSlots.Kind kind) {
            boolean needsQuotes = kind == ConfigColumnSlots.Kind.ENTRY_KEY
                    ? !StringUtil.isJavaIdentifier(column.name())
                    : PsiTreeUtil.getParentOfType(position, JSLiteralExpression.class, false) == null;
            return needsQuotes ? "\"" + column.name() + "\"" : column.name();
        }

        @NotNull
        private static String typeText(@NotNull ColumnInfo column) {
            return column.isRepeated() ? column.type() + "[]" : column.type();
        }
    }
}
