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
package io.github.rejeb.dataform.language.injection;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.lang.javascript.JavascriptLanguage;
import com.intellij.lang.javascript.psi.JSExpression;
import com.intellij.lang.javascript.psi.JSFunctionExpression;
import com.intellij.lang.javascript.psi.ecma6.JSStringTemplateExpression;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.sql.dialects.bigquery.BigQueryDialect;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SqlxJsQueryInjector implements MultiHostInjector {

    private static final Pattern REF_NAME =
            Pattern.compile("(?:ctx\\s*\\.\\s*)?(?:ref|resolve)\\s*\\(\\s*[\"']([^\"']+)[\"']\\s*\\)");

    @Override
    public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar,
                                     @NotNull PsiElement context) {
        if (!(context instanceof JSStringTemplateExpression template)) return;
        if (!(context instanceof PsiLanguageInjectionHost host)) return;
        if (!isDataformQueryTemplate(template)) return;

        List<JSExpression> holes = collectHoles(template);
        if (holes.isEmpty()) return;

        injectSql(registrar, host, template, holes);
        injectJsHoles(registrar, host, template, holes);
    }

    @NotNull
    @Override
    public List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
        return List.of(JSStringTemplateExpression.class);
    }

    private void injectSql(@NotNull MultiHostRegistrar registrar,
                           @NotNull PsiLanguageInjectionHost host,
                           @NotNull JSStringTemplateExpression template,
                           @NotNull List<JSExpression> holes) {
        int base = template.getTextRange().getStartOffset();
        int sqlEnd = template.getTextLength() - 1;

        registrar.startInjecting(BigQueryDialect.INSTANCE);

        int currentPos = 1;
        boolean hasFragment = false;

        for (JSExpression hole : holes) {
            int holeStart = hole.getTextRange().getStartOffset() - base - 2;
            int holeEnd = hole.getTextRange().getEndOffset() - base + 1;

            if (currentPos < holeStart) {
                registrar.addPlace(hasFragment ? "" : null, null, host, new TextRange(currentPos, holeStart));
                hasFragment = true;
            }

            registrar.addPlace(placeholderFor(hole), "", host, new TextRange(holeStart, holeStart));
            currentPos = holeEnd;
        }

        if (currentPos < sqlEnd) {
            registrar.addPlace(hasFragment ? "" : null, null, host, new TextRange(currentPos, sqlEnd));
            hasFragment = true;
        }

        if (hasFragment) {
            registrar.doneInjecting();
        }
    }

    private void injectJsHoles(@NotNull MultiHostRegistrar registrar,
                               @NotNull PsiLanguageInjectionHost host,
                               @NotNull JSStringTemplateExpression template,
                               @NotNull List<JSExpression> holes) {
        int base = template.getTextRange().getStartOffset();
        for (JSExpression hole : holes) {
            TextRange absolute = hole.getTextRange();
            TextRange relative =
                    new TextRange(absolute.getStartOffset() - base, absolute.getEndOffset() - base);
            registrar.startInjecting(JavascriptLanguage.INSTANCE);
            registrar.addPlace(null, null, host, relative);
            registrar.doneInjecting();
        }
    }

    @NotNull
    private static String placeholderFor(@NotNull JSExpression hole) {
        String text = hole.getText();
        if (text != null) {
            Matcher matcher = REF_NAME.matcher(text);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return "NULL";
    }

    @NotNull
    private static List<JSExpression> collectHoles(@NotNull JSStringTemplateExpression template) {
        List<JSExpression> holes = new ArrayList<>();
        for (PsiElement child = template.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof JSExpression expression) {
                holes.add(expression);
            }
        }
        return holes;
    }

    private static boolean isDataformQueryTemplate(@NotNull JSStringTemplateExpression template) {
        if (!(template.getParent() instanceof JSFunctionExpression)) return false;
        return isDataformDefinitionFile(template);
    }

    private static boolean isDataformDefinitionFile(@NotNull PsiElement element) {
        PsiFile topLevel = InjectedLanguageManager.getInstance(element.getProject())
                .getTopLevelFile(element);
        VirtualFile file = topLevel == null ? null : topLevel.getVirtualFile();
        if (file == null) return false;
        String path = file.getPath().replace('\\', '/');
        return path.contains("/definitions/") && (path.endsWith(".js") || path.endsWith(".ts"));
    }
}
