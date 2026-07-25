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
package io.github.rejeb.dataform.language.evaluation;

import com.intellij.lang.javascript.psi.JSCallExpression;
import com.intellij.lang.javascript.psi.JSExpression;
import com.intellij.lang.javascript.psi.JSReferenceExpression;
import com.intellij.lang.javascript.psi.ecma6.JSStringTemplateExpression;
import com.intellij.openapi.util.TextRange;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import io.github.rejeb.dataform.language.injection.SqlxJsQueryInjector;
import io.github.rejeb.dataform.language.psi.SharedTokenTypes;
import io.github.rejeb.dataform.language.psi.SqlxConfigBlock;
import io.github.rejeb.dataform.language.psi.SqlxJsBlock;
import io.github.rejeb.dataform.language.psi.SqlxJsLiteralExpression;
import io.github.rejeb.dataform.language.psi.SqlxSqlBlock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Collects the Dataform expressions of a PSI root that are candidates for value folding.
 *
 * <p>All methods must be called inside a read action, and the PSI elements they return must not
 * outlive it.</p>
 */
public final class DataformExpressionCollector {

    /**
     * One region to fold: the PSI element owning it, and the expression it renders. A single element
     * can own several parts, for instance when only the nested substitutions of an expression resolve.
     *
     * @param element    the element the fold region is anchored to
     * @param expression the expression whose value replaces the range
     */
    public record FoldablePart(@NotNull PsiElement element, @NotNull DataformExpression expression) {
    }

    public static final int MAX_SOURCE_LENGTH = 2_000;
    private static final String TEMPLATE_PREFIX = "${";
    private static final String TEMPLATE_SUFFIX = "}";

    private DataformExpressionCollector() {
    }

    /**
     * Collects the {@code ${...}} template expressions of a SQLX root, as found in SQL and
     * pre/post-operations blocks.
     */
    @NotNull
    public static List<DataformExpression> collectSqlxTemplates(@NotNull PsiElement root) {
        return collectSqlxTemplateElements(root).stream().map(FoldablePart::expression).toList();
    }

    /**
     * Same as {@link #collectSqlxTemplates(PsiElement)}, keyed by the element to fold.
     */
    @NotNull
    public static List<FoldablePart> collectSqlxTemplateElements(@NotNull PsiElement root) {
        List<FoldablePart> expressions = new ArrayList<>();
        PsiTreeUtil.processElements(root, element -> {
            if (!isTemplateExpression(element)
                    || PsiTreeUtil.getParentOfType(element, SqlxSqlBlock.class, false) == null) {
                return true;
            }
            addFoldableParts(element, element.getText(), element.getTextRange().getStartOffset(),
                    DataformExpressionKind.SQLX_TEMPLATE, expressions);
            return true;
        });
        return expressions;
    }

    /**
     * Collects the {@code ${...}} substitutions of JavaScript template literals in Dataform
     * definition files.
     */
    @NotNull
    public static List<DataformExpression> collectJsTemplateSubstitutions(@NotNull PsiElement root) {
        return collectJsTemplateSubstitutionElements(root).stream().map(FoldablePart::expression).toList();
    }

    /**
     * Same as {@link #collectJsTemplateSubstitutions(PsiElement)}, keyed by the element to fold.
     */
    @NotNull
    public static List<FoldablePart> collectJsTemplateSubstitutionElements(@NotNull PsiElement root) {
        if (!SqlxJsQueryInjector.isDataformDefinitionFile(root)) {
            return List.of();
        }
        List<FoldablePart> expressions = new ArrayList<>();
        for (JSStringTemplateExpression template :
                PsiTreeUtil.findChildrenOfType(root, JSStringTemplateExpression.class)) {
            for (JSExpression hole : SqlxJsQueryInjector.collectHoles(template)) {
                String source = hole.getText();
                if (source == null || source.isBlank()) {
                    continue;
                }
                TextRange range = substitutionRange(hole);
                addFoldableParts(hole, TEMPLATE_PREFIX + source + TEMPLATE_SUFFIX, range.getStartOffset(),
                        DataformExpressionKind.JS_TEMPLATE_SUBSTITUTION, expressions);
            }
        }
        return expressions;
    }

    /**
     * Collects the workflow-settings references of a JavaScript root whose value is a YAML scalar,
     * keyed by the element to fold.
     */
    @NotNull
    public static List<FoldablePart> collectWorkflowSettingsReferenceElements(@NotNull PsiElement root) {
        List<FoldablePart> expressions = new ArrayList<>();
        for (JSReferenceExpression reference : PsiTreeUtil.findChildrenOfType(root, JSReferenceExpression.class)) {
            String path = reference.getText();
            if (path == null || !path.contains(".")) {
                continue;
            }
            if (DataformWorkflowSettingsValueResolver.resolve(reference, path) == null) {
                continue;
            }
            boolean substitution = isTemplateSubstitution(reference);
            TextRange range = substitution ? substitutionRange(reference) : reference.getTextRange();
            String hostText = substitution ? TEMPLATE_PREFIX + path + TEMPLATE_SUFFIX : path;
            expressions.add(new FoldablePart(reference, new DataformExpression(path, hostText, range,
                    DataformExpressionKind.WORKFLOW_SETTINGS_REFERENCE)));
        }
        return expressions;
    }

    /**
     * Collects the references to {@code includes/*.js} exports of a JavaScript root, such as the
     * {@code columns} value of a config block, keyed by the element to fold.
     */
    @NotNull
    public static List<FoldablePart> collectIncludesReferenceElements(@NotNull PsiElement root,
                                                                      @NotNull Set<String> includeNames) {
        List<FoldablePart> expressions = new ArrayList<>();
        if (includeNames.isEmpty()) {
            return expressions;
        }
        for (JSReferenceExpression reference : PsiTreeUtil.findChildrenOfType(root, JSReferenceExpression.class)) {
            if (!isIncludesReference(reference, includeNames) || isCallee(reference)) {
                continue;
            }
            String source = reference.getText();
            if (source == null || source.length() > MAX_SOURCE_LENGTH) {
                continue;
            }
            boolean substitution = isTemplateSubstitution(reference);
            TextRange range = substitution ? substitutionRange(reference) : reference.getTextRange();
            String hostText = substitution ? TEMPLATE_PREFIX + source + TEMPLATE_SUFFIX : source;
            expressions.add(new FoldablePart(reference, new DataformExpression(source, hostText, range,
                    DataformExpressionKind.INCLUDES_REFERENCE)));
        }
        return expressions;
    }

    /**
     * Collects the includes references of the JavaScript injected in the {@code config} and
     * {@code js} blocks of a SQLX file. Only the expression sources are meaningful, since the ranges
     * belong to the injected documents.
     */
    @NotNull
    public static List<DataformExpression> collectInjectedIncludesReferences(@NotNull PsiFile sqlxFile,
                                                                             @NotNull Set<String> includeNames) {
        if (includeNames.isEmpty()) {
            return List.of();
        }
        InjectedLanguageManager manager = InjectedLanguageManager.getInstance(sqlxFile.getProject());
        List<DataformExpression> expressions = new ArrayList<>();
        for (PsiElement block : PsiTreeUtil.findChildrenOfAnyType(sqlxFile, SqlxConfigBlock.class, SqlxJsBlock.class)) {
            manager.enumerate(block, (injectedFile, places) ->
                    expressions.addAll(collectIncludesReferenceElements(injectedFile, includeNames).stream()
                            .map(FoldablePart::expression).toList()));
        }
        return expressions;
    }

    private static boolean isIncludesReference(@NotNull JSReferenceExpression reference,
                                               @NotNull Set<String> includeNames) {
        if (reference.getParent() instanceof JSReferenceExpression) {
            return false;
        }
        JSExpression qualifier = reference.getQualifier();
        if (qualifier == null) {
            return false;
        }
        JSExpression root = qualifier;
        while (root instanceof JSReferenceExpression qualified && qualified.getQualifier() != null) {
            root = qualified.getQualifier();
        }
        return includeNames.contains(root.getText());
    }

    private static boolean isCallee(@NotNull JSReferenceExpression reference) {
        return reference.getParent() instanceof JSCallExpression call && call.getMethodExpression() == reference;
    }

    private static boolean isTemplateSubstitution(@NotNull PsiElement element) {
        return element.getParent() instanceof JSStringTemplateExpression;
    }

    @NotNull
    private static TextRange substitutionRange(@NotNull PsiElement hole) {
        TextRange range = hole.getTextRange();
        return new TextRange(range.getStartOffset() - TEMPLATE_PREFIX.length(),
                range.getEndOffset() + TEMPLATE_SUFFIX.length());
    }

    /**
     * Adds the deterministic parts of a {@code ${...}} text: the whole expression, or the nested
     * substitutions of an expression whose value depends on the execution mode.
     */
    private static void addFoldableParts(@NotNull PsiElement element,
                                         @NotNull String templateText,
                                         int baseOffset,
                                         @NotNull DataformExpressionKind kind,
                                         @NotNull List<FoldablePart> expressions) {
        for (TextRange part : DataformTemplateSyntax.foldableRanges(templateText)) {
            String partText = part.substring(templateText);
            String source = DataformTemplateSyntax.sourceOf(partText);
            if (source.isBlank() || source.length() > MAX_SOURCE_LENGTH) {
                continue;
            }
            TextRange hostRange = new TextRange(baseOffset + part.getStartOffset(), baseOffset + part.getEndOffset());
            expressions.add(new FoldablePart(element, new DataformExpression(source, partText, hostRange, kind)));
        }
    }

    private static boolean isTemplateExpression(@NotNull PsiElement element) {
        return element instanceof SqlxJsLiteralExpression
                && element.getNode() != null
                && SharedTokenTypes.TEMPLATE_EXPRESSION.equals(element.getNode().getElementType());
    }

    @Nullable
    private static String unwrap(@NotNull String text) {
        if (!text.startsWith(TEMPLATE_PREFIX) || !text.endsWith(TEMPLATE_SUFFIX)) {
            return null;
        }
        String source = text.substring(TEMPLATE_PREFIX.length(), text.length() - TEMPLATE_SUFFIX.length());
        return source.isBlank() || source.length() > MAX_SOURCE_LENGTH ? null : source;
    }
}
