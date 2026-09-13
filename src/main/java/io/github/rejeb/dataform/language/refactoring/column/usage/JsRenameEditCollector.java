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
package io.github.rejeb.dataform.language.refactoring.column.usage;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.javascript.psi.JSLiteralExpression;
import com.intellij.lang.javascript.psi.JSNamedElement;
import com.intellij.lang.javascript.psi.JSReferenceExpression;
import com.intellij.lang.javascript.psi.ecma6.JSStringTemplateExpression;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import io.github.rejeb.dataform.language.index.DataformJsFileIndex;
import io.github.rejeb.dataform.language.injection.InjectedFiles;
import io.github.rejeb.dataform.language.psi.SqlxJsBlock;
import io.github.rejeb.dataform.language.psi.SqlxJsLiteralExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Collects the places of the project's JavaScript that write a column name as text.
 *
 * <p>A Dataform project builds SQL in JavaScript: an include helper takes a column name as a string,
 * a {@code js} block keeps a list of them. None of that resolves to anything, so these places are
 * found by matching the name as a whole identifier and are reported as usages the user reviews
 * rather than as certain ones.</p>
 *
 * <p>What declares the name is collected along with what reads it — a variable, a parameter, a
 * function, a key of an object — so that a file whose references are rewritten keeps referring to
 * something that exists.</p>
 *
 * <p>Template literals carrying injected SQL are skipped: their column names are real references and
 * are collected as SQL, and matching them again here would report the same place twice.</p>
 *
 * <p>The JavaScript of a SQLX file is searched only when the rename already touches that file. A
 * column name is not unique in a Dataform project — {@code customer_id} names a column of the orders
 * chain and another of the customers chain — so matching it in the block of an action the rename has
 * nothing to do with renames a column that was never asked for. The include files are searched
 * whatever the action: a helper is shared, and the name it is handed belongs to whoever calls it.</p>
 */
public final class JsRenameEditCollector {

    private JsRenameEditCollector() {
    }

    /**
     * The JavaScript places naming {@code oldName}: those of the include files of the project, and
     * those of the SQLX files the rename already touches. Walks the project, so it belongs to a
     * background read action.
     *
     * @param scope the SQLX files whose JavaScript names columns of this rename
     */
    public static @NotNull List<ColumnRenameEdit> collect(@NotNull Project project,
                                                          @NotNull String oldName,
                                                          @NotNull String newName,
                                                          @NotNull Collection<PsiFile> scope) {
        List<ColumnRenameEdit> edits = new ArrayList<>();
        for (PsiFile file : DataformJsFileIndex.findAllIncludeFiles(project)) {
            collectIn(file, oldName, newName, edits);
        }
        for (PsiFile file : scope) {
            for (PsiFile injected : injectedJavaScript(file)) {
                collectIn(injected, oldName, newName, edits);
            }
        }
        return edits;
    }

    private static void collectIn(@NotNull PsiFile file,
                                  @NotNull String oldName,
                                  @NotNull String newName,
                                  @NotNull List<ColumnRenameEdit> edits) {
        for (JSLiteralExpression literal : PsiTreeUtil.findChildrenOfType(file, JSLiteralExpression.class)) {
            if (!literal.isStringLiteral() || carriesInjection(literal)) continue;
            collectInLiteral(literal, oldName, newName, edits);
        }
        for (JSStringTemplateExpression template :
                PsiTreeUtil.findChildrenOfType(file, JSStringTemplateExpression.class)) {
            if (carriesInjection(template)) continue;
            collectInTemplate(template, oldName, newName, edits);
        }
        for (JSNamedElement named : PsiTreeUtil.findChildrenOfType(file, JSNamedElement.class)) {
            if (!oldName.equals(named.getName())) continue;
            PsiElement identifier = named.getNameIdentifier();
            if (identifier == null || identifier instanceof JSLiteralExpression) continue;
            add(edits, EditFactory.ofWhole(identifier, newName,
                    ColumnRenameEdit.Kind.JS_IDENTIFIER, ColumnRenameEdit.Risk.HEURISTIC,
                    "JavaScript declaration of " + oldName));
        }
        for (JSReferenceExpression reference :
                PsiTreeUtil.findChildrenOfType(file, JSReferenceExpression.class)) {
            if (!oldName.equals(reference.getReferenceName())) continue;
            PsiElement identifier = reference.getReferenceNameElement();
            if (identifier == null) continue;
            add(edits, EditFactory.ofWhole(identifier, newName,
                    ColumnRenameEdit.Kind.JS_IDENTIFIER, ColumnRenameEdit.Risk.HEURISTIC,
                    "JavaScript reference " + oldName));
        }
    }

    /**
     * A string literal. One that is exactly the column name is as good as a reference; one that
     * merely contains it holds SQL text the user has to look at.
     */
    private static void collectInLiteral(@NotNull JSLiteralExpression literal,
                                         @NotNull String oldName,
                                         @NotNull String newName,
                                         @NotNull List<ColumnRenameEdit> edits) {
        if (isModulePath(literal)) return;
        Object value = literal.getValue();
        if (oldName.equals(value)) {
            add(edits, EditFactory.ofLiteral(literal, newName,
                    ColumnRenameEdit.Kind.JS_STRING, ColumnRenameEdit.Risk.CERTAIN,
                    "JavaScript string " + oldName));
            return;
        }
        collectInText(literal, literal.getText(), oldName, newName,
                ColumnRenameEdit.Risk.HEURISTIC, edits);
    }

    /**
     * The text of a template literal, outside the expressions it substitutes.
     *
     * <p>A hole is JavaScript, not text: the literals it holds are collected where they are, and
     * matching them here again would produce a second edit over the same characters.</p>
     */
    private static void collectInTemplate(@NotNull JSStringTemplateExpression template,
                                          @NotNull String oldName,
                                          @NotNull String newName,
                                          @NotNull List<ColumnRenameEdit> edits) {
        int base = template.getTextRange().getStartOffset();
        List<TextRange> holes = new ArrayList<>();
        for (PsiElement argument : template.getArguments()) {
            TextRange range = argument.getTextRange();
            if (range != null) holes.add(range.shiftLeft(base));
        }
        for (TextRange range : IdentifierOccurrences.of(template.getText(), oldName)) {
            if (holes.stream().anyMatch(hole -> hole.intersects(range))) continue;
            add(edits, EditFactory.of(template, range, newName,
                    ColumnRenameEdit.Kind.JS_STRING, ColumnRenameEdit.Risk.HEURISTIC,
                    "JavaScript text " + oldName));
        }
    }

    private static void collectInText(@NotNull PsiElement anchor,
                                      @NotNull String text,
                                      @NotNull String oldName,
                                      @NotNull String newName,
                                      @NotNull ColumnRenameEdit.Risk risk,
                                      @NotNull List<ColumnRenameEdit> edits) {
        for (TextRange range : IdentifierOccurrences.of(text, oldName)) {
            add(edits, EditFactory.of(anchor, range, newName,
                    ColumnRenameEdit.Kind.JS_STRING, risk, "JavaScript text " + oldName));
        }
    }

    /** Whether the element hosts another language, whose own places are collected elsewhere. */
    private static boolean carriesInjection(@NotNull PsiElement element) {
        List<Pair<PsiElement, TextRange>> injected = InjectedLanguageManager
                .getInstance(element.getProject()).getInjectedPsiFiles(element);
        return injected != null && !injected.isEmpty();
    }

    /** Whether a literal is the path of a {@code require} or of an import, never a column name. */
    private static boolean isModulePath(@NotNull JSLiteralExpression literal) {
        Object value = literal.getValue();
        if (!(value instanceof String text)) return false;
        return text.contains("/") || text.endsWith(".js") || text.endsWith(".sqlx");
    }

    private static @NotNull List<PsiFile> injectedJavaScript(@NotNull PsiFile hostFile) {
        List<PsiElement> hosts = new ArrayList<>();
        hosts.addAll(PsiTreeUtil.findChildrenOfType(hostFile, SqlxJsBlock.class));
        hosts.addAll(PsiTreeUtil.findChildrenOfType(hostFile, SqlxJsLiteralExpression.class));
        return InjectedFiles.of(hosts);
    }

    private static void add(@NotNull List<ColumnRenameEdit> edits, @Nullable ColumnRenameEdit edit) {
        if (edit != null) edits.add(edit);
    }
}
