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
package io.github.rejeb.dataform.language.completion.config.partition;

import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds what the completion popup offers inside the {@code partitionBy} string of a config block,
 * be it the whole expression or the argument of the partitioning function it holds.
 */
public final class PartitionFormLookups {

    private static final double COLUMN_PRIORITY = 30;
    private static final double WRITTEN_PRIORITY = 20;
    private static final double FORM_PRIORITY = 10;

    private PartitionFormLookups() {
    }

    /**
     * Returns what is offered at the given cursor: the partitioning expressions the action columns
     * justify, the columns a partitioning function accepts, its truncation units or its bounds. The
     * caret is inside a string literal when {@code quoted}, in which case nothing is quoted again.
     */
    @NotNull
    public static List<LookupElement> of(@NotNull List<ColumnInfo> columns,
                                         @NotNull PartitionExpressionCursor cursor,
                                         boolean quoted) {
        return switch (cursor.target()) {
            case FORM -> forms(columns, cursor.tailLength(), quoted);
            case COLUMN -> columnArguments(columns, cursor);
            case GRANULARITY -> granularityArguments(cursor);
            case BOUNDARIES -> boundaryArguments(cursor);
            case NONE -> List.of();
        };
    }

    /**
     * Everything that partitions the action: the date columns naming a partition on their own, then
     * each form written on a column it accepts, so that typing a column name reaches the
     * expressions its type allows, then the forms with their column left to pick.
     */
    @NotNull
    private static List<LookupElement> forms(@NotNull List<ColumnInfo> columns,
                                             int tailLength,
                                             boolean quoted) {
        List<LookupElement> elements = new ArrayList<>();
        for (ColumnInfo column : PartitionColumns.ofTypes(columns, PartitionForm.BARE_COLUMN_TYPES)) {
            elements.add(bareColumn(column, tailLength, quoted));
        }
        for (PartitionForm form : PartitionForm.availableFor(columns)) {
            if (!form.takesColumn()) {
                elements.add(written(form, form.template(), form.label(), "", tailLength, quoted));
                continue;
            }
            for (String column : form.argumentsIn(columns)) {
                elements.add(written(form, form.templateOn(column), form.labelOn(column),
                        column, tailLength, quoted));
            }
        }
        for (PartitionForm form : PartitionForm.availableFor(columns)) {
            if (form.takesColumn() && !PartitionForm.namedColumnsOf(form, columns).isEmpty()) {
                elements.add(openForm(form, columns, tailLength, quoted));
            }
        }
        return elements;
    }

    /**
     * The columns the function at the cursor accepts, the ingestion time pseudo-column it also
     * takes included. They are offered even when the schema names none of that type, so that an
     * expression stays editable on a schema that is not known yet.
     */
    @NotNull
    private static List<LookupElement> columnArguments(@NotNull List<ColumnInfo> columns,
                                                       @NotNull PartitionExpressionCursor cursor) {
        PartitionForm form = cursor.form();
        if (form == null) {
            return List.of();
        }
        List<LookupElement> elements = new ArrayList<>();
        for (ColumnInfo column : PartitionColumns.ofTypes(columns, form.columnTypes())) {
            elements.add(LookupElementBuilder.create(column.name())
                    .withTypeText(column.type())
                    .withIcon(AllIcons.Nodes.Field)
                    .withInsertHandler(new ReplaceTailInsertHandler(cursor.tailLength())));
        }
        for (String pseudoColumn : form.pseudoColumns()) {
            elements.add(LookupElementBuilder.create(pseudoColumn)
                    .withTypeText("ingestion time")
                    .withIcon(AllIcons.Nodes.Field)
                    .withInsertHandler(new ReplaceTailInsertHandler(cursor.tailLength())));
        }
        return elements;
    }

    @NotNull
    private static List<LookupElement> granularityArguments(
            @NotNull PartitionExpressionCursor cursor) {
        PartitionForm form = cursor.form();
        if (form == null) {
            return List.of();
        }
        return form.granularities().stream()
                .map(granularity -> (LookupElement) LookupElementBuilder.create(granularity)
                        .withTypeText(form.functionName())
                        .withIcon(AllIcons.Nodes.Enum)
                        .withInsertHandler(new ReplaceTailInsertHandler(cursor.tailLength())))
                .toList();
    }

    /**
     * The bounds of a range partition. BigQuery accepts a single call there, so the one proposal is
     * that call, opened on the constants it takes.
     */
    @NotNull
    private static List<LookupElement> boundaryArguments(
            @NotNull PartitionExpressionCursor cursor) {
        PartitionForm form = cursor.form();
        if (form == null || form.boundaries().isEmpty()) {
            return List.of();
        }
        return List.of(LookupElementBuilder.create(form.boundariesLabel())
                .withIcon(AllIcons.Nodes.Function)
                .withTypeText(form.functionName())
                .withInsertHandler(new PartitionTemplateInsertHandler(
                        form.boundaries(), form, List.of(), true, cursor.tailLength())));
    }

    @NotNull
    private static LookupElement bareColumn(@NotNull ColumnInfo column,
                                            int tailLength,
                                            boolean quoted) {
        return prioritized(LookupElementBuilder
                .create(quoted ? column.name() : "\"" + column.name() + "\"")
                .withLookupString(column.name())
                .withPresentableText(column.name())
                .withTypeText(column.type())
                .withIcon(AllIcons.Nodes.Field)
                .withInsertHandler(new ReplaceTailInsertHandler(tailLength)), COLUMN_PRIORITY);
    }

    /**
     * A form written on a column, which the popup matches on the column name as well as on the
     * expression, so that naming the column is a way in of its own.
     */
    @NotNull
    private static LookupElement written(@NotNull PartitionForm form,
                                         @NotNull String template,
                                         @NotNull String label,
                                         @NotNull String column,
                                         int tailLength,
                                         boolean quoted) {
        LookupElementBuilder element = LookupElementBuilder
                .create(quoted || template.indexOf('$') >= 0 ? label : "\"" + label + "\"")
                .withLookupString(label)
                .withPresentableText(label)
                .withIcon(AllIcons.Nodes.Function)
                .withTypeText(column.isEmpty() ? "ingestion time" : typeTextOf(form));
        if (!column.isEmpty()) {
            element = element.withLookupString(column);
        }
        element = template.indexOf('$') >= 0
                ? element.withInsertHandler(new PartitionTemplateInsertHandler(
                        template, form, List.of(), quoted, tailLength))
                : element.withInsertHandler(new ReplaceTailInsertHandler(tailLength));
        return prioritized(element, WRITTEN_PRIORITY);
    }

    /**
     * A form with its column left to pick, which the template opens the popup on.
     */
    @NotNull
    private static LookupElement openForm(@NotNull PartitionForm form,
                                          @NotNull List<ColumnInfo> columns,
                                          int tailLength,
                                          boolean quoted) {
        return prioritized(LookupElementBuilder.create(form.label())
                .withPresentableText(form.label())
                .withIcon(AllIcons.Nodes.Function)
                .withTypeText(typeTextOf(form))
                .withInsertHandler(new PartitionTemplateInsertHandler(form.template(), form,
                        PartitionForm.namedColumnsOf(form, columns), quoted, tailLength)),
                FORM_PRIORITY);
    }

    @NotNull
    private static String typeTextOf(@NotNull PartitionForm form) {
        return form.columnTypes().isEmpty()
                ? "ingestion time"
                : String.join(", ", form.columnTypes());
    }

    @NotNull
    private static LookupElement prioritized(@NotNull LookupElementBuilder element, double priority) {
        return PrioritizedLookupElement.withPriority(element, priority);
    }
}
