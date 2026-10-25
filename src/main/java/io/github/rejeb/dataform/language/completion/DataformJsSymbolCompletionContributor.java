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

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.javascript.JavascriptLanguage;
import com.intellij.lang.javascript.psi.JSFile;
import com.intellij.lang.javascript.psi.JSFunction;
import com.intellij.lang.javascript.psi.JSVariable;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import io.github.rejeb.dataform.language.index.DataformJsFileIndex;
import io.github.rejeb.dataform.language.injection.InjectedFiles;
import io.github.rejeb.dataform.language.psi.SqlxFile;
import io.github.rejeb.dataform.language.service.DataformCoreIndexService;
import io.github.rejeb.dataform.language.service.DataformFunctionCompletionObject;
import io.github.rejeb.dataform.language.service.WorkflowSettingsService;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Stream;

public class DataformJsSymbolCompletionContributor extends CompletionContributor {

    public DataformJsSymbolCompletionContributor() {
        extend(
                CompletionType.BASIC,
                PlatformPatterns.psiElement(),
                new Provider()
        );
    }

    @Override
    public void fillCompletionVariants(@NotNull CompletionParameters parameters,
                                       @NotNull CompletionResultSet result) {
        super.fillCompletionVariants(parameters, result);

        if (result.isStopped() || !Provider.appliesTo(parameters)) {
            return;
        }
        Set<String> shadowed = Provider
                .shadowedIncludeNames(parameters.getPosition().getProject());
        if (shadowed.isEmpty()) {
            return;
        }
        result.runRemainingContributors(parameters, completionResult -> {
            if (!shadowed.contains(completionResult.getLookupElement().getLookupString())) {
                result.passResult(completionResult);
            }
        });
    }

    private static final class Provider extends CompletionProvider<CompletionParameters> {

        private static final InsertHandler<LookupElement> CALL_PARENTHESES = (ctx, item) -> {
            Editor editor = ctx.getEditor();
            int offset = editor.getCaretModel().getOffset();
            editor.getDocument().insertString(offset, "()");
            editor.getCaretModel().moveToOffset(offset + 1);
            AutoPopupController.getInstance(ctx.getProject()).scheduleAutoPopup(editor);
        };
        @Override
        protected void addCompletions(@NotNull CompletionParameters parameters, @NotNull ProcessingContext context, @NotNull CompletionResultSet result) {

            if (!appliesTo(parameters)) {
                return;
            }

            PsiElement position = parameters.getPosition();
            PsiFile topLevelFile = InjectedLanguageManager.getInstance(position.getProject()).getTopLevelFile(position);

            Project project = position.getProject();
            result.addAllElements(handleJsBlockContent(topLevelFile));
            result.addAllElements(handleIncludeExports(project));
            result.addAllElements(handleBuiltinFunctions(project));
            result.addAllElements(handleBuiltinVariables(project));
        }

        /**
         * Tells whether Dataform symbols apply to the given completion position.
         */
        static boolean appliesTo(@NotNull CompletionParameters parameters) {
            PsiElement position = parameters.getPosition();
            PsiFile originalFile = parameters.getOriginalFile();

            if (!(originalFile instanceof JSFile)) {
                return false;
            }

            PsiFile topLevelFile = InjectedLanguageManager.getInstance(position.getProject()).getTopLevelFile(position);

            if (!(topLevelFile instanceof SqlxFile) && !(topLevelFile instanceof JSFile)) {
                return false;
            }

            if (topLevelFile instanceof JSFile && topLevelFile == originalFile) {
                return false;
            }

            return !isAfterDot(position);
        }

        /**
         * Returns the names proposed as qualified include paths, which must not also be
         * proposed on their own by the JavaScript contributors.
         */
        static Set<String> shadowedIncludeNames(@NotNull Project project) {
            Set<String> names = new HashSet<>();
            DataformJsFileIndex.getAllExports(project).forEach((fileName, exports) -> {
                names.add(fileName);
                exports.forEach(export -> names.add(export.exportName()));
            });
            return names;
        }

        private static boolean isAfterDot(@NotNull PsiElement position) {
            PsiElement previous = PsiTreeUtil.prevVisibleLeaf(position);
            return previous != null && ".".equals(previous.getText());
        }

        private List<LookupElement> handleJsBlockContent(PsiFile file) {
            List<PsiFile> jsBlocks = findJsBlock(file);
            return Stream.concat(
                    jsBlocks.stream()
                            .flatMap(psiFile -> PsiTreeUtil.findChildrenOfType(psiFile, JSVariable.class).stream())
                            .filter(variable -> variable.getName() != null)
                            .map(this::buildJsVarElemLookup),
                    jsBlocks.stream()
                            .flatMap(psiFile -> PsiTreeUtil.findChildrenOfType(psiFile, JSFunction.class).stream())
                            .map(DataformFunctionCompletionObject::fromJSFunction)
                            .flatMap(Optional::stream)
                            .map(this::buildJsFunctionElemLookup)).toList();
        }

        private List<LookupElement> handleIncludeExports(Project project) {
            return DataformJsFileIndex.getAllExports(project).entrySet().stream()
                    .flatMap(entry -> entry.getValue().stream()
                            .map(export -> buildIncludeExportElemLookup(entry.getKey(), export)))
                    .toList();
        }

        private LookupElement buildIncludeExportElemLookup(String fileName,
                                                           DataformJsFileIndex.IncludeExport export) {
            LookupElementBuilder element = LookupElementBuilder
                    .create(fileName + "." + export.exportName())
                    .withLookupString(export.exportName())
                    .withTypeText("include")
                    .withIcon(export.isFunction() ? AllIcons.Nodes.Function : AllIcons.Nodes.Variable);

            if (!export.isFunction()) {
                return element;
            }
            return element.withTailText("()", true).withInsertHandler(CALL_PARENTHESES);
        }

        private List<LookupElement> handleBuiltinFunctions(Project project) {
            return DataformCoreIndexService.getInstance(project)
                    .getCachedDataformFunctionsForCompletion()
                    .stream()
                    .map(this::buildJsFunctionElemLookup).toList();
        }

        private List<LookupElement> handleBuiltinVariables(Project project) {
            WorkflowSettingsService wfService = WorkflowSettingsService.getInstance(project);
            return DataformCoreIndexService.getInstance(project)
                    .getCachedDataformVariablesRef()
                    .stream()
                    .filter(variable -> variable.getName() != null && wfService.isWorkflowSettingProperty(variable.getName()))
                    .map(this::buildJsVarElemLookup).toList();
        }

        private List<PsiFile> findJsBlock(PsiFile file) {
            return InjectedFiles.inside(file, PsiLanguageInjectionHost.class).stream()
                    .filter(injected -> injected.getLanguage() == JavascriptLanguage.INSTANCE)
                    .toList();
        }

        private LookupElement buildJsVarElemLookup(JSVariable variable) {
            return LookupElementBuilder
                    .create(variable.getName())
                    .withTypeText("Dataform")
                    .withIcon(AllIcons.Nodes.Variable)
                    .withBoldness(true);
        }

        private LookupElement buildJsFunctionElemLookup(DataformFunctionCompletionObject function) {
            return LookupElementBuilder
                    .create(function.name())
                    .withTypeText("Dataform")
                    .withIcon(AllIcons.Nodes.Function)
                    .withInsertHandler(function.signature().isEmpty() ? null : CALL_PARENTHESES)
                    .withTailText(function.signature(), true).withBoldness(true);
        }
    }
}
