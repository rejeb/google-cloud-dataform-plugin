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
package io.github.rejeb.dataform.language.unittest.completion;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import io.github.rejeb.dataform.language.schema.sql.DataformActionColumns;
import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;
import io.github.rejeb.dataform.language.unittest.schema.TestBlockSchema;
import io.github.rejeb.dataform.language.unittest.schema.TestSchemaResolver;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

/**
 * Completes the alias of a select item, or of a field of a {@code STRUCT}, in a Dataform unit test
 * with the columns of the table the block stands for: the table an input is named after, or the
 * tested dataset for the expected output.
 */
public class SqlxTestColumnCompletionContributor extends CompletionContributor {

    private static final double COLUMN_PRIORITY = 500.0;
    private static final double SHAPE_BONUS = 100.0;
    private static final double ORDER_STEP = 0.001;

    public SqlxTestColumnCompletionContributor() {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), new Provider());
    }

    private static final class Provider extends CompletionProvider<CompletionParameters> {

        @Override
        protected void addCompletions(@NotNull CompletionParameters parameters,
                                      @NotNull ProcessingContext context,
                                      @NotNull CompletionResultSet result) {
            PsiElement position = parameters.getPosition();
            Optional<TestAliasSlot> slot = TestAliasSlot.at(position);
            if (slot.isEmpty()) {
                return;
            }
            Optional<TestBlockSchema> schema = TestSchemaResolver.getInstance(position.getProject()).resolveAt(position);
            if (schema.isEmpty()) {
                return;
            }
            List<ColumnInfo> columns = DataformActionColumns.descend(schema.get().columns(), slot.get().recordPath());
            boolean added = false;
            for (int i = 0; i < columns.size(); i++) {
                ColumnInfo column = columns.get(i);
                if (slot.get().isUsed(column.name())) {
                    continue;
                }
                result.addElement(lookup(column, priority(column, slot.get().shape(), i)));
                added = true;
            }
            if (added) {
                result.stopHere();
            }
        }

        private static double priority(@NotNull ColumnInfo column, @NotNull TestAliasSlot.ValueShape shape, int index) {
            boolean matches = switch (shape) {
                case STRUCT -> column.isRecord() && !column.isRepeated();
                case ARRAY -> column.isRepeated();
                case SCALAR -> false;
            };
            return COLUMN_PRIORITY + (matches ? SHAPE_BONUS : 0.0) - index * ORDER_STEP;
        }

        @NotNull
        private static LookupElement lookup(@NotNull ColumnInfo column, double priority) {
            String type = column.isRecord() ? "STRUCT" : column.type();
            LookupElementBuilder element = LookupElementBuilder.create(column.name())
                    .withTypeText(column.isRepeated() ? type + "[]" : type)
                    .withIcon(AllIcons.Nodes.Field);
            if (column.description() != null && !column.description().isBlank()) {
                element = element.withTailText(" " + column.description(), true);
            }
            return PrioritizedLookupElement.withPriority(element, priority);
        }
    }
}
