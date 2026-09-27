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
package io.github.rejeb.dataform.language.completion;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.lang.javascript.psi.JSArgumentList;
import com.intellij.lang.javascript.psi.JSCallExpression;
import com.intellij.lang.javascript.psi.JSExpression;
import com.intellij.lang.javascript.psi.JSLiteralExpression;
import com.intellij.lang.javascript.psi.JSReferenceExpression;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import io.github.rejeb.dataform.language.compilation.DataformCompilationService;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.compilation.model.ProjectConfig;
import io.github.rejeb.dataform.language.service.WorkflowSettingsProperty;
import io.github.rejeb.dataform.language.service.WorkflowSettingsService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Optional;

/**
 * Completes the arguments of {@code ref()} and {@code resolve()}, which Dataform reads from the end:
 * name, schema, project. The first positional argument offers the default project, the schemas, then
 * the tables. The second offers the schemas and the tables, or only the tables of the schema the first
 * argument names when that one is no project. The third offers the tables of the schema the second
 * names. Any other argument offers the tables.
 */
public class DataformRefCompletionContributor extends CompletionContributor {

    public DataformRefCompletionContributor() {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), new Provider());
    }

    private static final class Provider extends CompletionProvider<CompletionParameters> {

        @Override
        protected void addCompletions(@NotNull CompletionParameters parameters,
                                      @NotNull ProcessingContext context,
                                      @NotNull CompletionResultSet result) {
            JSLiteralExpression literal = refLiteral(parameters.getPosition());
            if (literal == null) {
                return;
            }
            CompiledGraph graph = literal.getProject().getService(DataformCompilationService.class)
                    .getCompiledGraph();
            if (graph == null) {
                result.stopHere();
                return;
            }
            JSExpression[] arguments = literal.getParent() instanceof JSArgumentList list
                    ? list.getArguments()
                    : new JSExpression[0];
            int position = Arrays.asList(arguments).indexOf(literal);
            String defaultProject = defaultProject(parameters, graph);
            String schema = null;
            if (position == 0) {
                if (defaultProject != null) {
                    result.addElement(DataformActionLookups.project(defaultProject));
                }
                result.addAllElements(DataformActionLookups.schemas(graph));
            } else if (position == 1) {
                String first = stringValue(arguments[0]);
                if (first != null && !first.equals(defaultProject) && !DataformActionLookups.isDatabase(graph, first)) {
                    schema = first;
                } else {
                    result.addAllElements(DataformActionLookups.schemas(graph));
                }
            } else if (position == 2) {
                schema = stringValue(arguments[1]);
            }
            result.addAllElements(DataformActionLookups.tablesIn(graph, schema));
            result.addAllElements(DataformActionLookups.declarationsIn(graph, schema));
            result.stopHere();
        }

        @Nullable
        private static String defaultProject(@NotNull CompletionParameters parameters,
                                             @NotNull CompiledGraph graph) {
            PsiFile hostFile = InjectedLanguageManager.getInstance(parameters.getOriginalFile().getProject())
                    .getTopLevelFile(parameters.getOriginalFile());
            WorkflowSettingsProperty dataform = WorkflowSettingsService.getInstance(hostFile.getProject())
                    .getWorkflowProperties(hostFile.getVirtualFile())
                    .get("dataform");
            String configured = Optional.ofNullable(dataform)
                    .map(WorkflowSettingsProperty::children)
                    .map(children -> children.get("projectConfig"))
                    .map(WorkflowSettingsProperty::children)
                    .map(children -> children.get("defaultProject"))
                    .map(WorkflowSettingsProperty::value)
                    .filter(value -> !value.isBlank())
                    .orElse(null);
            if (configured != null) {
                return configured;
            }
            ProjectConfig compiled = graph.getProjectConfig();
            return compiled == null || compiled.getDefaultDatabase() == null || compiled.getDefaultDatabase().isBlank()
                    ? null
                    : compiled.getDefaultDatabase();
        }

        @Nullable
        private static String stringValue(@NotNull JSExpression expression) {
            return expression instanceof JSLiteralExpression literal
                    && literal.isQuotedLiteral()
                    && literal.getValue() instanceof String value
                    ? value
                    : null;
        }

        @Nullable
        private static JSLiteralExpression refLiteral(@NotNull PsiElement position) {
            JSLiteralExpression literal =
                    PsiTreeUtil.getParentOfType(position, JSLiteralExpression.class, false);
            if (literal == null || !literal.isQuotedLiteral()) {
                return null;
            }
            JSCallExpression call = PsiTreeUtil.getParentOfType(literal, JSCallExpression.class);
            if (call == null) {
                return null;
            }
            JSExpression method = call.getMethodExpression();
            if (!(method instanceof JSReferenceExpression reference)) {
                return null;
            }
            String name = reference.getReferenceName();
            return "ref".equals(name) || "resolve".equals(name) ? literal : null;
        }
    }
}
