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
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.icons.AllIcons;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import io.github.rejeb.dataform.language.compilation.DataformCompilationService;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.completion.DataformActionLookups;
import io.github.rejeb.dataform.language.psi.SharedTokenTypes;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SqlxInputNameCompletionContributor extends CompletionContributor {

    private static final double DEPENDENCY_PRIORITY = 300.0;

    public SqlxInputNameCompletionContributor() {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(SharedTokenTypes.INPUT_NAME), new Provider());
    }

    private static final class Provider extends CompletionProvider<CompletionParameters> {

        @Override
        protected void addCompletions(@NotNull CompletionParameters parameters,
                                      @NotNull ProcessingContext context,
                                      @NotNull CompletionResultSet result) {
            PsiElement position = parameters.getPosition();
            CompiledGraph graph = DataformCompilationService.getInstance(position.getProject()).getCompiledGraph();
            if (graph == null) {
                result.stopHere();
                return;
            }
            CompletionResultSet names = result.withPrefixMatcher(prefix(position, parameters.getOffset()));
            Set<String> seen = new HashSet<>();
            SqlxUnitTests.testedDataset(parameters.getOriginalFile())
                    .flatMap(graph::findTableByReference)
                    .ifPresent(table -> table.getDependencyTargets().forEach(target -> {
                        if (target != null && target.getName() != null && seen.add(target.getName())) {
                            names.addElement(DataformActionLookups.lookup(target, AllIcons.Nodes.DataTables,
                                    "dependency", DEPENDENCY_PRIORITY));
                        }
                    }));
            List<LookupElement> others = new ArrayList<>(DataformActionLookups.tables(graph));
            others.addAll(DataformActionLookups.declarations(graph));
            for (LookupElement other : others) {
                if (seen.add(other.getLookupString())) {
                    names.addElement(other);
                }
            }
            result.stopHere();
        }

        @NotNull
        private static String prefix(@NotNull PsiElement position, int caretOffset) {
            String text = position.getText();
            int end = caretOffset - position.getTextRange().getStartOffset();
            return end > 1 && end <= text.length() ? text.substring(1, end) : "";
        }
    }
}
