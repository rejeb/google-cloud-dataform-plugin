/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
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
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
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
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import io.github.rejeb.dataform.language.index.DataformJsFileIndex;
import io.github.rejeb.dataform.language.psi.SqlxFile;
import io.github.rejeb.dataform.language.service.DataformCoreIndexService;
import io.github.rejeb.dataform.language.service.DataformFunctionCompletionObject;
import io.github.rejeb.dataform.language.service.WorkflowSettingsService;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class DataformJsSymbolCompletionContributorProvider extends CompletionProvider<CompletionParameters> {

    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters, @NotNull ProcessingContext context, @NotNull CompletionResultSet result) {

        PsiElement position = parameters.getPosition();
        PsiFile originalFile = parameters.getOriginalFile();

        if (!(originalFile instanceof JSFile)) {
            return;
        }

        PsiFile topLevelFile = InjectedLanguageManager.getInstance(position.getProject()).getTopLevelFile(position);

        if (!(topLevelFile instanceof SqlxFile) && !(topLevelFile instanceof JSFile)) {
            return;
        }

        if (topLevelFile instanceof JSFile && topLevelFile == originalFile) {
            return;
        }

        if (isAfterDot(position)) {
            return;
        }

        Project project = position.getProject();
        result.addAllElements(handleJsBlockContent(topLevelFile,project));
        result.addAllElements(handleFileNameCompletion(project));
        result.addAllElements(handleBuiltinFunctions(project));
        result.addAllElements(handleBuiltinVariables(project));
        result.addAllElements(handleDataformWorkflowSettings(project));
    }

    private boolean isAfterDot(PsiElement position) {
        PsiElement prevLeaf = PsiTreeUtil.prevLeaf(position);
        while (prevLeaf != null && prevLeaf.getText().trim().isEmpty()) {
            prevLeaf = PsiTreeUtil.prevLeaf(prevLeaf);
        }

        return prevLeaf != null && ".".equals(prevLeaf.getText());
    }

    private List<LookupElement> handleJsBlockContent(PsiFile file, Project project) {
        List<PsiFile> jsBlocks = findJsBlock(file, project);

        Collection<LookupElement> variables = jsBlocks
                .stream()
                .flatMap(psiFile -> PsiTreeUtil.findChildrenOfType(psiFile, JSVariable.class).stream())
                .map(this::buildJsVarElemLookup)
                .toList();

        List<LookupElement> functions = jsBlocks
                .stream()
                .flatMap(psiFile -> PsiTreeUtil.findChildrenOfType(psiFile, JSFunction.class)
                        .stream())
                .map(DataformFunctionCompletionObject::fromJSFunction)
                .flatMap(Optional::stream)
                .map(this::buildJsFunctionElemLookup)
                .toList();

        List<LookupElement> resultElements = new ArrayList<>();
        resultElements.addAll(variables);
        resultElements.addAll(functions);
        return resultElements;
    }

    private List<LookupElement> handleFileNameCompletion(Project project) {

        Map<String, List<DataformJsFileIndex.IncludeExport>> exportsByFile = DataformJsFileIndex.getAllExports(project);

        List<LookupElement> resultElements = new ArrayList<>();
        for (String fileName : exportsByFile.keySet()) {
            resultElements.add(LookupElementBuilder.create(fileName)
                    .withTypeText("include")
                    .withIcon(AllIcons.FileTypes.JavaScript)
                    .withInsertHandler((ctx, item) -> {
                        Editor editor = ctx.getEditor();
                        int offset = editor.getCaretModel().getOffset();
                        editor.getDocument().insertString(offset, ".");
                        editor.getCaretModel().moveToOffset(offset + 1);
                        AutoPopupController.getInstance(ctx.getProject()).scheduleAutoPopup(editor);
                    }));
        }
        return resultElements;
    }

    private List<LookupElement> handleBuiltinFunctions(Project project) {
        return DataformCoreIndexService.getInstance(project)
                .getCachedDataformFunctionsForCompletion()
                .stream()
                .map(this::buildJsFunctionElemLookup).toList();
    }

    private List<LookupElement> handleBuiltinVariables(Project project) {
        return DataformCoreIndexService.getInstance(project)
                .getCachedDataformVariablesRef()
                .stream()
                .map(this::buildJsVarElemLookup).toList();
    }

    private List<LookupElement> handleDataformWorkflowSettings(Project project) {
        List<LookupElement> resultElements = new ArrayList<>();
        WorkflowSettingsService service = WorkflowSettingsService.getInstance(project);
        Collection<String> properties = service.getPropertiesForPrefix(null);

        for (String property : properties) {
            LookupElementBuilder element = LookupElementBuilder.create(property).withTypeText("workflow_settings.yaml").withIcon(AllIcons.Json.Object);
            WorkflowSettingsService.WorkflowSettingsProperty prop = service.getWorkflowProperties().get(property);

            if (prop != null && prop.hasChildren()) {
                element = element.withInsertHandler((ctx, item) -> {
                    Editor editor = ctx.getEditor();
                    int offset = editor.getCaretModel().getOffset();
                    editor.getDocument().insertString(offset, ".");
                    editor.getCaretModel().moveToOffset(offset + 1);
                    AutoPopupController.getInstance(ctx.getProject()).scheduleAutoPopup(editor);
                });
            }


            resultElements.add(element);
        }
        return resultElements;
    }

    private List<PsiFile> findJsBlock(PsiFile file, Project project) {
        List<PsiFile> jsBlocks = new ArrayList<>();
        Collection<PsiLanguageInjectionHost> hosts = PsiTreeUtil.collectElementsOfType(file, PsiLanguageInjectionHost.class);

        for (PsiLanguageInjectionHost host : hosts) {
            List<Pair<PsiElement, TextRange>> injectedPsi = InjectedLanguageManager.getInstance(host.getProject()).getInjectedPsiFiles(host);

            if (injectedPsi != null) {
                for (Pair<PsiElement, TextRange> pair : injectedPsi) {
                    PsiFile injectedFile = pair.first.getContainingFile();
                    if (injectedFile.getLanguage() == JavascriptLanguage.INSTANCE) {
                        jsBlocks.add(injectedFile);
                    }
                }
            }
        }
        return jsBlocks;
    }

    private LookupElement buildJsVarElemLookup(JSVariable variable) {
        return LookupElementBuilder
                .create(variable.getName())
                .withTypeText("Dataform")
                .withIcon(AllIcons.Nodes.Variable)
                .withInsertHandler((insertContext, item) -> {
                    if (variable.getQualifiedName() != null) {
                        Editor editor = insertContext.getEditor();
                        int offset = editor.getCaretModel().getOffset();
                        editor.getDocument().insertString(offset, "");
                        editor.getCaretModel().moveToOffset(offset + 1);
                    }
                }).withBoldness(true);
    }

    private LookupElement buildJsFunctionElemLookup(DataformFunctionCompletionObject function) {
        return LookupElementBuilder
                .create(function.name())
                .withTypeText("Dataform")
                .withIcon(AllIcons.Nodes.Function)
                .withInsertHandler((insertContext, item) -> {
                    if (!function.signature().isEmpty()) {
                        Editor editor = insertContext.getEditor();
                        int offset = editor.getCaretModel().getOffset();
                        editor.getDocument().insertString(offset, "()");
                        editor.getCaretModel().moveToOffset(offset + 1);
                    }
                }).withTailText(function.signature(), true).withBoldness(true);
    }
}
