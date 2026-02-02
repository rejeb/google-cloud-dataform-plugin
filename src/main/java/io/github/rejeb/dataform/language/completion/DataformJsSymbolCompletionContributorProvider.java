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
import com.intellij.lang.javascript.psi.JSFile;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import io.github.rejeb.dataform.language.index.DataformJsFileIndex;
import io.github.rejeb.dataform.language.psi.SqlxFile;
import io.github.rejeb.dataform.language.service.DataformCoreIndexService;
import io.github.rejeb.dataform.language.service.DataformFunctionCompletionObject;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class DataformJsSymbolCompletionContributorProvider extends CompletionProvider<CompletionParameters> {

    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters,
                                  @NotNull ProcessingContext context,
                                  @NotNull CompletionResultSet result) {

        PsiElement position = parameters.getPosition();
        PsiFile originalFile = parameters.getOriginalFile();

        if (!(originalFile instanceof JSFile)) {
            return;
        }

        PsiFile topLevelFile = InjectedLanguageManager.getInstance(position.getProject())
                .getTopLevelFile(position);

        if (!(topLevelFile instanceof SqlxFile) &&
                !(topLevelFile instanceof JSFile)) {
            return;
        }

        if (topLevelFile instanceof JSFile && topLevelFile == originalFile) {
            return;
        }

        if (isAfterDot(position)) {
            return;
        }

        Project project = position.getProject();
        result.addAllElements(handleFileNameCompletion(project));
        result.addAllElements(handleBuiltinFunctions(project));

    }

    private boolean isAfterDot(PsiElement position) {
        PsiElement prevLeaf = PsiTreeUtil.prevLeaf(position);
        while (prevLeaf != null && prevLeaf.getText().trim().isEmpty()) {
            prevLeaf = PsiTreeUtil.prevLeaf(prevLeaf);
        }

        return prevLeaf != null && ".".equals(prevLeaf.getText());
    }

    private List<LookupElement> handleFileNameCompletion(Project project) {

        Map<String, List<DataformJsFileIndex.IncludeExport>> exportsByFile =
                DataformJsFileIndex.getAllExports(project);

        List<LookupElement> resultElements = new ArrayList<>();
        for (String fileName : exportsByFile.keySet()) {
            resultElements.add(
                    LookupElementBuilder.create(fileName)
                            .withTypeText("include")
                            .withIcon(AllIcons.FileTypes.JavaScript)
                            .withInsertHandler((ctx, item) -> {

                                Editor editor = ctx.getEditor();
                                int offset = editor.getCaretModel().getOffset();
                                editor.getDocument().insertString(offset, ".");
                                editor.getCaretModel().moveToOffset(offset + 1);


                                AutoPopupController.getInstance(ctx.getProject())
                                        .scheduleAutoPopup(editor);
                            })
            );
        }
        return resultElements;
    }

    private List<LookupElement> handleBuiltinFunctions(Project project) {
        Collection<DataformFunctionCompletionObject> functions =
                DataformCoreIndexService.getInstance(project).getCachedDataformFunctionsForCompletion();
        List<LookupElement> resultElements = new ArrayList<>();
        for (DataformFunctionCompletionObject function : functions) {
            resultElements.add(LookupElementBuilder.create(function.name())
                    .withTypeText("Dataform")
                    .withIcon(AllIcons.Nodes.Function)
                    .withInsertHandler((insertContext, item) -> {
                        if (!function.signature().isEmpty()) {
                            Editor editor = insertContext.getEditor();
                            int offset = editor.getCaretModel().getOffset();
                            editor.getDocument().insertString(offset, "()");
                            editor.getCaretModel().moveToOffset(offset + 1);
                        }
                    })
                    .withTailText(function.signature(), true)
                    .withBoldness(true));

        }
        return resultElements;
    }
}
