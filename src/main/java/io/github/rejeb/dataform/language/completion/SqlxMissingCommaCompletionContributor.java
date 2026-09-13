/*
 * Copyright 2025 Rejeb Ben Rejeb
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.rejeb.dataform.language.completion;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionInitializationContext;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.sql.dialects.bigquery.BigQueryDialect;
import com.intellij.sql.psi.SqlIdentifierKeywordTokenType;
import com.intellij.sql.psi.SqlSelectClause;
import com.intellij.sql.psi.SqlTokens;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Set;

/**
 * Keeps column completion alive while a new select list item is typed ahead of an existing one,
 * before the comma separating them has been written.
 *
 * <p>Completion runs on a copy of the file in which a dummy identifier stands at the caret. For
 * {@code SELECT cust| t.customer_id FROM t} that copy reads {@code SELECT custXXX t.customer_id
 * FROM t}, which the BigQuery parser recovers from by closing the query right after the dummy
 * identifier: the {@code FROM} clause is left outside the query, the reference at the caret has
 * no table to draw columns from, and the popup stays empty. The generic SQL parser keeps the
 * clause and completes as expected.</p>
 *
 * <p>Writing the dummy identifier with a trailing comma turns the copy into the well-formed
 * {@code SELECT custXXX, t.customer_id FROM t}. Only the copy is touched: what the user picks
 * replaces the prefix typed in the document, and the comma remains theirs to write.</p>
 *
 * <p>The identifier is changed only in SQL injected into a SQLX file, only when the word at the
 * caret opens a select list item, right after {@code SELECT}, its qualifier or a comma, and only
 * when the next token begins another expression. A caret before {@code FROM}, a comma, a closing
 * parenthesis or an alias keyword, or inside a nested expression, is left to the SQL plugin as
 * it is.</p>
 */
public class SqlxMissingCommaCompletionContributor extends CompletionContributor {

    private static final Set<IElementType> EXPRESSION_STARTS = Set.of(
            SqlTokens.SQL_IDENT,
            SqlTokens.SQL_IDENT_DELIMITED,
            SqlTokens.SQL_STRING_TOKEN,
            SqlTokens.SQL_INTEGER_TOKEN,
            SqlTokens.SQL_FLOAT_TOKEN);

    private static final Set<String> ITEM_OPENERS = Set.of("SELECT", "DISTINCT", "ALL");

    @Override
    public void beforeCompletion(@NotNull CompletionInitializationContext context) {
        PsiFile file = context.getFile();
        if (!(file.getLanguage() instanceof BigQueryDialect) || !isInSqlxFile(file)) return;
        if (isMissingCommaPosition(file, context.getStartOffset())) {
            context.setDummyIdentifier(CompletionInitializationContext.DUMMY_IDENTIFIER_TRIMMED + ", ");
        }
    }

    /**
     * Whether the caret at {@code offset} is on a bare select list item that is directly followed
     * by the start of another expression instead of the comma that should separate them.
     *
     * <p>The item is read from the text rather than from the tree: the word being typed may lex as
     * a keyword ({@code order} on the way to {@code order_date}), and the tree around it is what
     * the parser could recover, not what the user means. The tokens on either side of the word
     * are reliable: the one before it belongs to the intact part of the select list, the one
     * after it is the first thing the parser gave up on.</p>
     */
    static boolean isMissingCommaPosition(@NotNull PsiFile file, int offset) {
        CharSequence text = file.getViewProvider().getContents();
        if (offset < text.length() && isIdentifierChar(text.charAt(offset))) return false;
        int itemStart = startOfQualifiedName(text, offset);
        PsiElement before = itemStart > 0 ? file.findElementAt(itemStart - 1) : null;
        if (before == null) return false;
        PsiElement previous = before instanceof PsiWhiteSpace ? PsiTreeUtil.prevVisibleLeaf(before) : before;
        if (previous == null || !opensASelectListItem(previous)) return false;
        PsiElement after = offset < text.length() ? file.findElementAt(offset) : null;
        if (after == null) return false;
        PsiElement next = after instanceof PsiWhiteSpace ? PsiTreeUtil.nextVisibleLeaf(after) : after;
        return next != null && startsAnExpression(next);
    }

    /**
     * The offset where the possibly qualified name ending at {@code offset} begins: the word at
     * the caret, preceded by any number of {@code qualifier.} segments, so that {@code t.co} and
     * {@code t.} are read as one item like {@code co} is.
     */
    private static int startOfQualifiedName(@NotNull CharSequence text, int offset) {
        int start = offset;
        while (true) {
            while (start > 0 && isIdentifierChar(text.charAt(start - 1))) start--;
            if (start > 1 && text.charAt(start - 1) == '.' && isIdentifierChar(text.charAt(start - 2))) {
                start--;
                continue;
            }
            return start;
        }
    }

    private static boolean isIdentifierChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    /**
     * Whether a select list item may start right after {@code leaf}: the {@code SELECT} keyword,
     * its {@code DISTINCT} or {@code ALL} qualifier, or a comma of the select list.
     */
    private static boolean opensASelectListItem(@NotNull PsiElement leaf) {
        if (!(leaf.getParent() instanceof SqlSelectClause)) return false;
        String text = leaf.getText();
        return ",".equals(text) || ITEM_OPENERS.contains(text.toUpperCase(Locale.ROOT));
    }

    private static boolean startsAnExpression(@NotNull PsiElement leaf) {
        IElementType type = leaf.getNode().getElementType();
        return EXPRESSION_STARTS.contains(type)
                || type instanceof SqlIdentifierKeywordTokenType
                || "(".equals(leaf.getText());
    }

    private static boolean isInSqlxFile(@NotNull PsiFile file) {
        PsiFile host = InjectedLanguageManager.getInstance(file.getProject()).getTopLevelFile(file);
        return host != null && host.getName().endsWith(".sqlx");
    }
}
