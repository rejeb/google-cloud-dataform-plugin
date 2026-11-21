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

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.icons.AllIcons;
import com.intellij.lang.javascript.psi.JSLiteralExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import io.github.rejeb.dataform.language.compilation.DataformCompilationService;
import io.github.rejeb.dataform.language.completion.StringValueLookups;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.config.ConfigSchemaLookup;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.TreeSet;

/**
 * Completes the tags already used in the project when editing the {@code tags}
 * list of a SQLX config block.
 */
final class ConfigTagProvider extends CompletionProvider<CompletionParameters> {

    private static final String TAGS_PROPERTY = "tags";

    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters,
                                  @NotNull ProcessingContext context,
                                  @NotNull CompletionResultSet result) {
        PsiElement position = parameters.getPosition();
        CompiledGraph graph = DataformCompilationService
                .getInstance(position.getProject()).getCompiledGraph();
        if (graph == null) {
            return;
        }
        Set<String> tags = new TreeSet<>(graph.getTags());
        tags.removeAll(ConfigSchemaLookup.siblingStrings(position));
        if (tags.isEmpty()) {
            return;
        }
        boolean quoted = PsiTreeUtil.getParentOfType(position, JSLiteralExpression.class, false)
                != null;
        for (String tag : tags) {
            result.addElement(StringValueLookups.of(tag, !quoted)
                    .withTypeText(TAGS_PROPERTY)
                    .withIcon(AllIcons.Nodes.Tag));
        }
        result.stopHere();
    }
}
