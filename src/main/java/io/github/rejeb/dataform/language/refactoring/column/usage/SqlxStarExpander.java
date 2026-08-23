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

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import io.github.rejeb.dataform.language.refactoring.column.DataformColumnNameValidator;
import io.github.rejeb.dataform.language.schema.sql.DataformActionColumns;
import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Turns a {@code SELECT *} into the explicit list of the columns the action produces.
 *
 * <p>A starred query names none of its columns, so a rename has nowhere to write. Expanding the star
 * gives every column a line of its own, and the renamed one is written {@code order_id AS order_ref}
 * — the query keeps reading what its source calls the column, and starts producing it under the new
 * name. That holds whatever the source turns out to be, which matters here precisely because the
 * source is what could not be determined.</p>
 *
 * <p>Expanding changes what the action does: columns appearing in the source later will no longer
 * flow through on their own. The user is asked before this runs.</p>
 */
public final class SqlxStarExpander {

    /**
     * The text a star expands to, or the reasons it cannot.
     *
     * @param text     the replacement of the star, {@code null} when there is none
     * @param blockers why the star cannot be expanded, empty when it can
     */
    public record Expansion(@Nullable String text, @NotNull List<String> blockers) {

        /** Whether the star can be expanded. */
        public boolean isPossible() {
            return text != null && blockers.isEmpty();
        }
    }

    private SqlxStarExpander() {
    }

    /**
     * The expansion of {@code star} in the file building the table, renaming {@code oldName} to
     * {@code newName} on the way.
     */
    public static @NotNull Expansion of(@NotNull PsiFile hostFile,
                                        @NotNull PsiElement star,
                                        @NotNull String oldName,
                                        @NotNull String newName) {
        List<String> blockers = new ArrayList<>();
        List<ColumnInfo> columns = DataformActionColumns.in(hostFile);
        if (columns.isEmpty()) {
            blockers.add("the schema of the action is not known; compile the project first");
        }
        String tail = tailOf(star);
        if (startsWithKeyword(tail, "REPLACE")) {
            blockers.add("the star carries a REPLACE list, which is not rewritten");
        }
        if (star.getText().endsWith(".*") && countStars(star) > 1) {
            blockers.add("the query selects several qualified stars");
        }
        if (!blockers.isEmpty()) return new Expansion(null, List.copyOf(blockers));

        Set<String> excluded = exceptNames(tail);
        String qualifier = qualifierOf(star);
        List<String> items = new ArrayList<>();
        for (ColumnInfo column : columns) {
            if (excluded.contains(column.name().toLowerCase(Locale.ROOT))) continue;
            items.add(itemFor(column.name(), qualifier, oldName, newName));
        }
        if (items.isEmpty()) {
            return new Expansion(null,
                    List.of("the star would expand to no column at all"));
        }
        return new Expansion(String.join(separatorOf(star), items), List.of());
    }

    /**
     * One item of the expanded list. The renamed column keeps reading its source under the old name
     * and is aliased to the new one; every other column is written as it is.
     */
    private static @NotNull String itemFor(@NotNull String columnName,
                                           @Nullable String qualifier,
                                           @NotNull String oldName,
                                           @NotNull String newName) {
        String read = (qualifier == null ? "" : qualifier + ".")
                + DataformColumnNameValidator.inSql(columnName);
        return columnName.equalsIgnoreCase(oldName)
                ? read + " AS " + DataformColumnNameValidator.inSql(newName)
                : read;
    }

    /**
     * How the items are joined: one per line under the star's own indentation when the select list
     * already spans several lines, and on one line otherwise.
     */
    private static @NotNull String separatorOf(@NotNull PsiElement star) {
        PsiElement clause = star.getParent();
        if (clause == null || !clause.getText().contains("\n")) return ", ";
        return ",\n" + indentationOf(star);
    }

    private static @NotNull String indentationOf(@NotNull PsiElement star) {
        PsiFile file = star.getContainingFile();
        if (file == null) return "";
        String text = file.getText();
        int offset = star.getTextRange().getStartOffset();
        int lineStart = text.lastIndexOf('\n', Math.max(0, offset - 1)) + 1;
        StringBuilder indentation = new StringBuilder();
        for (int i = lineStart; i < offset && i < text.length(); i++) {
            char character = text.charAt(i);
            if (character != ' ' && character != '\t') break;
            indentation.append(character);
        }
        return indentation.toString();
    }

    private static @Nullable String qualifierOf(@NotNull PsiElement star) {
        String text = star.getText();
        int dot = text.lastIndexOf('.');
        return dot <= 0 ? null : text.substring(0, dot);
    }

    private static @NotNull String tailOf(@NotNull PsiElement star) {
        PsiElement clause = star.getParent();
        if (clause == null) return "";
        String text = clause.getText();
        int from = star.getTextRange().getEndOffset() - clause.getTextRange().getStartOffset();
        return from < 0 || from >= text.length() ? "" : text.substring(from);
    }

    private static boolean startsWithKeyword(@NotNull String tail, @NotNull String keyword) {
        return tail.stripLeading().toUpperCase(Locale.ROOT).startsWith(keyword);
    }

    private static @NotNull Set<String> exceptNames(@NotNull String tail) {
        Set<String> names = new LinkedHashSet<>();
        if (!startsWithKeyword(tail, "EXCEPT")) return names;
        int open = tail.indexOf('(');
        int close = tail.indexOf(')', open + 1);
        if (open < 0 || close < 0) return names;
        for (String name : tail.substring(open + 1, close).split(",")) {
            String trimmed = name.trim().replace("`", "");
            if (!trimmed.isEmpty()) names.add(trimmed.toLowerCase(Locale.ROOT));
        }
        return names;
    }

    private static int countStars(@NotNull PsiElement star) {
        PsiElement clause = star.getParent();
        if (clause == null) return 1;
        int count = 0;
        for (PsiElement child : clause.getChildren()) {
            if (SqlxStarDeclarationLocator.isStar(child)) count++;
        }
        return count;
    }
}
