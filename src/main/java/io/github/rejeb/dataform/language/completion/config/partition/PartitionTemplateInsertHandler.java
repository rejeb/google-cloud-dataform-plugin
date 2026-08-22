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

import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.ConstantNode;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import io.github.rejeb.dataform.language.completion.config.ConfigInsertion;
import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Writes the template a picked proposal describes, be it a whole partitioning expression or the
 * second argument of one, and runs the caret through its placeholders, offering at each of them the
 * values the form accepts.
 */
public class PartitionTemplateInsertHandler implements InsertHandler<LookupElement> {

    private final String template;
    private final PartitionForm form;
    private final List<ColumnInfo> columns;
    private final boolean quoted;
    private final int tailLength;

    public PartitionTemplateInsertHandler(@NotNull String template,
                                          @NotNull PartitionForm form,
                                          @NotNull List<ColumnInfo> columns,
                                          boolean quoted,
                                          int tailLength) {
        this.template = template;
        this.form = form;
        this.columns = columns;
        this.quoted = quoted;
        this.tailLength = tailLength;
    }

    @Override
    public void handleInsert(@NotNull InsertionContext context, @NotNull LookupElement item) {
        Project project = context.getProject();
        Editor editor = ConfigInsertion.hostEditor(context);
        int start = ConfigInsertion.hostOffset(context, context.getStartOffset());
        int end = ConfigInsertion.hostOffset(context, context.getTailOffset());

        Document document = editor.getDocument();
        document.deleteString(start, Math.min(document.getTextLength(), end + tailLength));
        PsiDocumentManager.getInstance(project).commitDocument(document);
        editor.getCaretModel().moveToOffset(start);

        TemplateManager.getInstance(project).startTemplate(editor, build(project));
    }

    @NotNull
    private Template build(@NotNull Project project) {
        Template built = TemplateManager.getInstance(project).createTemplate("", "");
        built.setToReformat(false);
        if (!quoted) {
            built.addTextSegment("\"");
        }
        addSegments(built);
        if (!quoted) {
            built.addTextSegment("\"");
        }
        built.addEndVariable();
        return built;
    }

    /**
     * Splits the form template on the placeholder marks, the odd parts naming the placeholders and
     * the even ones being written as they are.
     */
    private void addSegments(@NotNull Template built) {
        String[] parts = template.split("\\$", -1);
        for (int i = 0; i < parts.length; i++) {
            if (i % 2 == 0) {
                built.addTextSegment(parts[i]);
            } else {
                Expression expression = expressionFor(parts[i]);
                built.addVariable(parts[i], expression, expression, true);
            }
        }
    }

    @NotNull
    private Expression expressionFor(@NotNull String variable) {
        if (PartitionForm.COLUMN.equals(variable)) {
            return new ChoiceExpression(columns.stream().map(ColumnInfo::name).toList(),
                    columnPresentation());
        }
        if (PartitionForm.GRANULARITY.equals(variable)) {
            return new ChoiceExpression(orderedGranularities());
        }
        return new ConstantNode(PartitionForm.numericDefaults().getOrDefault(variable, ""));
    }

    /**
     * The granularities of the form, the one the label shows coming first so that the placeholder
     * starts on the value the user just saw.
     */
    @NotNull
    private List<String> orderedGranularities() {
        if (form.granularities().isEmpty()) {
            return List.of();
        }
        String preferred = form.defaultGranularity();
        return Stream.concat(Stream.of(preferred),
                        form.granularities().stream().filter(value -> !value.equals(preferred)))
                .toList();
    }

    @NotNull
    private Function<String, LookupElement> columnPresentation() {
        Map<String, String> types = columns.stream()
                .collect(Collectors.toMap(ColumnInfo::name, ColumnInfo::type, (a, b) -> a));
        return name -> LookupElementBuilder.create(name)
                .withTypeText(types.getOrDefault(name, ""))
                .withIcon(AllIcons.Nodes.Field);
    }
}
