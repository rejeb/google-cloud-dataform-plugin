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

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.PlainPrefixMatcher;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.javascript.psi.JSFunctionExpression;
import com.intellij.lang.javascript.psi.JSParameter;
import com.intellij.lang.javascript.psi.ecma6.JSStringTemplateExpression;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.sql.psi.SqlFile;
import com.intellij.util.ProcessingContext;
import io.github.rejeb.dataform.language.injection.SqlxJsQueryInjector;
import io.github.rejeb.dataform.language.psi.SqlxFile;
import io.github.rejeb.dataform.language.util.SqlxEditors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class SqlxTemplateExpressionCompletionProvider extends CompletionProvider<CompletionParameters> {

    private static final int MAX_PREFIX_LENGTH = 16;
    private static final String DEFAULT_CONTEXT_NAME = "ctx";

    private record Template(String text, String typeText, int caretOffsetFromEnd) {
    }

    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters,
                                  @NotNull ProcessingContext context,
                                  @NotNull CompletionResultSet result) {

        PsiElement position = parameters.getPosition();
        PsiFile file = position.getContainingFile();
        PsiFile topLevelFile = InjectedLanguageManager.getInstance(position.getProject()).getTopLevelFile(position);

        List<Template> templates = templatesFor(position, file, topLevelFile);
        if (templates == null) {
            return;
        }

        String prefix = dollarPrefix(file.getText(), parameters.getOffset());
        if (prefix == null) {
            return;
        }

        int hostDollarOffset = hostOffsetOf(parameters, prefix);

        CompletionResultSet dollarResult = result.withPrefixMatcher(new PlainPrefixMatcher(prefix));
        for (Template template : templates) {
            LookupElementBuilder element = LookupElementBuilder.create(template.text())
                    .withTypeText(template.typeText())
                    .withBoldness(true)
                    .withInsertHandler((ctx, item) -> moveCaretInside(ctx, template, hostDollarOffset));
            dollarResult.addElement(element);
        }
    }

    @Nullable
    private static List<Template> templatesFor(@NotNull PsiElement position,
                                               @NotNull PsiFile file,
                                               @Nullable PsiFile topLevelFile) {
        if (topLevelFile instanceof SqlxFile) {
            return file instanceof SqlFile ? templates("") : null;
        }
        if (topLevelFile == null || !SqlxJsQueryInjector.isDataformDefinitionFile(topLevelFile.getOriginalFile())) {
            return null;
        }
        JSStringTemplateExpression queryTemplate = enclosingQueryTemplate(position);
        return queryTemplate == null ? null : templates(contextParameterName(queryTemplate) + ".");
    }

    @NotNull
    private static List<Template> templates(@NotNull String qualifier) {
        return List.of(
                new Template("${}", "Dataform expression", 1),
                new Template("${" + qualifier + "ref(\"\")}", "Dataform reference", 3),
                new Template("${" + qualifier + "self()}", "Dataform self table", 0));
    }

    @Nullable
    private static JSStringTemplateExpression enclosingQueryTemplate(@NotNull PsiElement position) {
        PsiElement host = InjectedLanguageManager.getInstance(position.getProject()).getInjectionHost(position);
        PsiElement anchor = host != null ? host : position;
        return PsiTreeUtil.getParentOfType(anchor, JSStringTemplateExpression.class, false);
    }

    @NotNull
    private static String contextParameterName(@NotNull JSStringTemplateExpression template) {
        JSFunctionExpression function =
                PsiTreeUtil.getParentOfType(template, JSFunctionExpression.class, true);
        if (function == null) {
            return DEFAULT_CONTEXT_NAME;
        }
        JSParameter[] parameters = function.getParameterVariables();
        if (parameters.length == 0) {
            return DEFAULT_CONTEXT_NAME;
        }
        String name = parameters[0].getName();
        return name == null || name.isEmpty() ? DEFAULT_CONTEXT_NAME : name;
    }

    private static int hostOffsetOf(@NotNull CompletionParameters parameters, @NotNull String prefix) {
        PsiFile originalFile = parameters.getOriginalFile();
        int dollarOffset = parameters.getOffset() - prefix.length();
        InjectedLanguageManager manager = InjectedLanguageManager.getInstance(originalFile.getProject());
        return manager.isInjectedFragment(originalFile)
                ? manager.injectedToHost(originalFile, dollarOffset)
                : dollarOffset;
    }

    private static void moveCaretInside(@NotNull InsertionContext ctx,
                                        @NotNull Template template,
                                        int hostDollarOffset) {
        int caretShift = template.text().length() - template.caretOffsetFromEnd();
        Editor editor = ctx.getEditor();
        Editor hostEditor = SqlxEditors.host(editor);
        int hostCaret = hostDollarOffset + caretShift;
        ctx.setLaterRunnable(() -> hostEditor.getCaretModel().moveToOffset(hostCaret));
    }

    @Nullable
    private static String dollarPrefix(@NotNull String text, int caretOffset) {
        if (caretOffset <= 0 || caretOffset > text.length()) {
            return null;
        }
        int start = caretOffset;
        while (start > 0 && caretOffset - start < MAX_PREFIX_LENGTH) {
            char c = text.charAt(start - 1);
            if (c == '$') {
                return text.substring(start - 1, caretOffset);
            }
            if (!Character.isLetter(c) && c != '{' && c != '.') {
                return null;
            }
            start--;
        }
        return null;
    }
}
