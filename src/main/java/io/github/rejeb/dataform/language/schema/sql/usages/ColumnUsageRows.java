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
package io.github.rejeb.dataform.language.schema.sql.usages;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.sql.psi.SqlCompositeElementTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the lines of the column window: what the column is built from, then what reads it,
 * grouped under Declaration and Usages headings carrying their counts.
 *
 * <p>Everything the reference search finds reads the column, so it is a usage. The declarations
 * are the columns this one is built from, which the file states rather than a search finding: a
 * rename has one, an expression over several columns has one for each.</p>
 *
 * <p>The search is bounded and runs under a read action. It is the reference search Find Usages
 * already runs, so a row here and a row in the Find window come from the same place.</p>
 */
public final class ColumnUsageRows {

    /**
     * Ceiling on the reads collected. The window opens on a click, so an unbounded project-wide
     * search would be paid for at exactly the moment the user is waiting.
     */
    static final int MAX_READS = 50;

    private ColumnUsageRows() {
    }

    /**
     * The rows for a column, headings included. Empty when the column has neither a declaration
     * that can be located nor a single read.
     */
    public static @NotNull List<ColumnUsageRow> of(@NotNull Project project,
                                                   @NotNull ColumnWindowTarget target,
                                                   @Nullable PsiElement caretReference) {
        return ReadAction.compute(() -> collect(project, target, caretReference));
    }

    /** Headings in the order the window shows them. */
    public static final String DECLARATION = "DECLARATION";
    public static final String USAGES = "USAGES";

    private static @NotNull List<ColumnUsageRow> collect(@NotNull Project project,
                                                         @NotNull ColumnWindowTarget target,
                                                         @Nullable PsiElement caretReference) {
        Map<String, ColumnUsageRow> declarations = new LinkedHashMap<>();
        Map<String, ColumnUsageRow> usages = new LinkedHashMap<>();
        String name = target.name();

        for (PsiElement declaration : target.declarations()) {
            if (isSameElement(declaration, caretReference) || isSelf(declaration, target)) continue;
            ColumnUsageRow row = row(project, declaration, name, DECLARATION,
                    ColumnUsageRow.Kind.DECLARATION);
            if (row != null) declarations.putIfAbsent(row.location(), row);
        }

        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        for (PsiElement searched : target.searchTargets()) {
            ReferencesSearch.search(searched, scope).forEach(found -> {
                PsiElement element = found.getElement();
                if (isSameElement(element, caretReference) || isSelf(element, target)) return true;
                ColumnUsageRow row = row(project, element, name, USAGES,
                        ColumnUsageRow.Kind.USAGE);
                if (row != null && !declarations.containsKey(row.location())) {
                    usages.putIfAbsent(row.location(), row);
                }
                return usages.size() < MAX_READS;
            });
        }

        List<ColumnUsageRow> rows = new ArrayList<>();
        if (!declarations.isEmpty()) {
            rows.add(ColumnUsageRow.heading(DECLARATION, declarations.size()));
            rows.addAll(declarations.values());
        }
        if (!usages.isEmpty()) {
            rows.add(ColumnUsageRow.heading(USAGES, usages.size()));
            rows.addAll(usages.values());
        }
        return rows;
    }

    /**
     * Whether an element is the column's own name where it is written.
     *
     * <p>An alias is both what later queries read and the place it is written, so the search finds
     * it and would list the column as reading itself. The caret reference does not catch it: a
     * column reference is what the caret usually sits on, and an alias is not one.</p>
     */
    private static boolean isSelf(@NotNull PsiElement element, @NotNull ColumnWindowTarget target) {
        for (PsiElement searched : target.searchTargets()) {
            if (isSameElement(element, searched)) return true;
        }
        return false;
    }

    /**
     * Whether two elements are the same occurrence. The reference search reports an element of its
     * own for the place the caret sits on, so identity alone does not recognise it.
     */
    private static boolean isSameElement(@NotNull PsiElement element, @Nullable PsiElement other) {
        if (other == null) return false;
        if (element == other) return true;
        if (element.getContainingFile() != other.getContainingFile()) return false;
        return element.getTextRange().intersects(other.getTextRange());
    }

    /**
     * A row for one occurrence: the line it sits on, split around the column name so the name can
     * be drawn apart from the expression that reads it.
     */
    private static @Nullable ColumnUsageRow row(@NotNull Project project,
                                                @NotNull PsiElement element,
                                                @NotNull String columnName,
                                                @NotNull String group,
                                                @NotNull ColumnUsageRow.Kind kind) {
        PsiFile containing = element.getContainingFile();
        if (containing == null) return null;
        InjectedLanguageManager manager = InjectedLanguageManager.getInstance(project);
        PsiFile host = manager.getTopLevelFile(containing);
        Document document = PsiDocumentManager.getInstance(project).getDocument(host);
        if (document == null) return null;

        VirtualFile hostFile = host.getVirtualFile();
        if (hostFile == null) return null;

        PsiElement name = lastIdentifier(element);
        PsiElement anchor = name == null ? element : name;
        int offset = manager.injectedToHost(anchor, anchor.getTextOffset());
        if (offset < 0 || offset >= document.getTextLength()) return null;

        int line = document.getLineNumber(offset);
        int lineStart = document.getLineStartOffset(line);
        String text = document.getText().substring(lineStart, document.getLineEndOffset(line));
        int at = offset - lineStart;
        String shown = nameOf(name, element, columnName);
        if (at < 0 || at > text.length()) return null;

        String before = text.substring(0, at).stripLeading();
        int end = Math.min(text.length(), at + shown.length());
        String after = text.substring(end);

        return ColumnUsageRow.entry(kind, group, before, shown, after,
                host.getName() + ":" + (line + 1),
                new OpenFileDescriptor(project, hostFile, offset));
    }

    /**
     * The name a row shows, which is the name at that place and not the name the window was opened
     * on: a column is declared under the name its own file gives it, and saying otherwise tells the
     * reader the declaring table has a column it does not have.
     */
    private static @NotNull String nameOf(@Nullable PsiElement identifier,
                                          @NotNull PsiElement element,
                                          @NotNull String fallback) {
        PsiElement source = identifier != null ? identifier : element;
        String text = source.getText();
        if (text == null || text.isEmpty() || text.contains("\n")) return fallback;
        return text.replace("`", "");
    }

    private static @Nullable PsiElement lastIdentifier(@NotNull PsiElement element) {
        PsiElement last = null;
        for (PsiElement child : element.getChildren()) {
            if (child.getNode() != null
                    && child.getNode().getElementType() == SqlCompositeElementTypes.SQL_IDENTIFIER) {
                last = child;
            }
        }
        return last;
    }
}
