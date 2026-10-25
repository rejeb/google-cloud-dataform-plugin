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
package io.github.rejeb.dataform.language.unittest;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.icons.AllIcons;
import com.intellij.lang.javascript.psi.JSLiteralExpression;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import io.github.rejeb.dataform.language.compilation.DataformCompilationService;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.completion.DataformActionLookups;
import io.github.rejeb.dataform.language.config.ConfigSchemaLookup;
import org.jetbrains.annotations.NotNull;

public class DataformTestDatasetCompletionContributor extends CompletionContributor {

    public DataformTestDatasetCompletionContributor() {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), new Provider());
    }

    private static final class Provider extends CompletionProvider<CompletionParameters> {

        @Override
        protected void addCompletions(@NotNull CompletionParameters parameters,
                                      @NotNull ProcessingContext context,
                                      @NotNull CompletionResultSet result) {
            PsiElement position = parameters.getPosition();
            if (!isTestedDatasetValue(position)) {
                return;
            }
            CompiledGraph graph = DataformCompilationService.getInstance(position.getProject()).getCompiledGraph();
            if (graph == null) {
                return;
            }
            graph.getTables().stream()
                    .filter(table -> TestableActions.isTestable(table) && !table.isDisabled())
                    .map(table -> DataformActionLookups.lookup(table.getTarget(), AllIcons.Nodes.DataTables,
                            table.getActionKind(), DataformActionLookups.ACTION_PRIORITY))
                    .forEach(result::addElement);
            result.stopHere();
        }

        private static boolean isTestedDatasetValue(@NotNull PsiElement position) {
            if (!ConfigSchemaLookup.isInConfigBlock(position)) {
                return false;
            }
            JSLiteralExpression literal = PsiTreeUtil.getParentOfType(position, JSLiteralExpression.class, false);
            return literal != null && SqlxUnitTests.isTestedDatasetString(literal);
        }
    }
}
