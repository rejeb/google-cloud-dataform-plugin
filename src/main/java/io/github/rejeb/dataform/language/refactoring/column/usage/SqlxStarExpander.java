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

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import io.github.rejeb.dataform.language.refactoring.column.DataformColumnNameValidator;
import io.github.rejeb.dataform.language.schema.sql.DataformActionColumns;
import io.github.rejeb.dataform.language.schema.sql.SqlPsiParts;
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
     * @param text          the replacement of the star, {@code null} when there is none
     * @param replacedLength how many characters from the start of the star the replacement covers:
     *                      the star itself and the {@code EXCEPT} list the expansion has already
     *                      applied
     * @param blockers      why the star cannot be expanded, empty when it can
     */
    public record Expansion(@Nullable String text, int replacedLength,
                            @NotNull List<String> blockers) {

        /** Whether the star can be expanded. */
        public boolean isPossible() {
            return text != null && blockers.isEmpty();
        }
    }

    /**
     * The modifier written after a star.
     *
     * @param excluded the lower-cased names of an {@code EXCEPT} list, empty when there is none
     * @param length   how many characters of the tail the modifier takes
     */
    private record Modifier(@NotNull Set<String> excluded, int length) {
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
        String token = SqlPsiParts.starTokenOf(star);
        String tail = tailOf(star);
        Modifier modifier = modifierOf(tail);
        int replacedLength = token.length() + modifier.length();
        if (startsWithKeyword(tail.substring(modifier.length()), "REPLACE")) {
            blockers.add("the star carries a REPLACE list, which is not rewritten");
        }
        if (hasItemsBesideTheStar(star, replacedLength)) {
            blockers.add("the select list holds items beside the star");
        }
        if (!blockers.isEmpty()) return new Expansion(null, 0, List.copyOf(blockers));

        String qualifier = qualifierOf(star);
        List<String> items = new ArrayList<>();
        for (ColumnInfo column : columns) {
            if (modifier.excluded().contains(column.name().toLowerCase(Locale.ROOT))) continue;
            items.add(itemFor(column.name(), qualifier, oldName, newName));
        }
        if (items.isEmpty()) {
            return new Expansion(null, 0,
                    List.of("the star would expand to no column at all"));
        }
        return new Expansion(String.join(separatorOf(star), items), replacedLength, List.of());
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
        String token = SqlPsiParts.starTokenOf(star);
        int dot = token.lastIndexOf('.');
        return dot <= 0 ? null : token.substring(0, dot);
    }

    /**
     * What is written after the {@code *} itself: the rest of the star element when the parser keeps
     * the {@code EXCEPT} list inside it, followed by the rest of the select list.
     */
    private static @NotNull String tailOf(@NotNull PsiElement star) {
        String own = star.getText().substring(SqlPsiParts.starTokenOf(star).length());
        PsiElement clause = star.getParent();
        if (clause == null) return own;
        String text = clause.getText();
        int from = star.getTextRange().getEndOffset() - clause.getTextRange().getStartOffset();
        return from < 0 || from >= text.length() ? own : own + text.substring(from);
    }

    private static boolean startsWithKeyword(@NotNull String tail, @NotNull String keyword) {
        return tail.stripLeading().toUpperCase(Locale.ROOT).startsWith(keyword);
    }

    /**
     * The {@code EXCEPT} list written after the star, which the expansion applies itself and
     * therefore replaces along with the star.
     */
    private static @NotNull Modifier modifierOf(@NotNull String tail) {
        if (!startsWithKeyword(tail, "EXCEPT")) return new Modifier(Set.of(), 0);
        int open = tail.indexOf('(');
        int close = tail.indexOf(')', open + 1);
        if (open < 0 || close < 0) return new Modifier(Set.of(), 0);
        Set<String> names = new LinkedHashSet<>();
        for (String name : tail.substring(open + 1, close).split(",")) {
            String trimmed = name.trim().replace("`", "");
            if (!trimmed.isEmpty()) names.add(trimmed.toLowerCase(Locale.ROOT));
        }
        return new Modifier(names, close + 1);
    }

    /**
     * Whether the select list holds anything besides the star and the modifier the expansion
     * replaces along with it.
     *
     * <p>What the expansion writes is the column list of the action, which is the whole output of
     * the query. That is the expansion of the star only when the star is all the query selects:
     * beside another item the list would name that item's column twice — once as itself and once
     * inside the expansion — and beside a second star each would be written the whole output.</p>
     *
     * <p>The keyword, the commas and the spacing of the clause are tokens, not items, and what the
     * expansion replaces along with the star — the {@code EXCEPT} list the parser keeps beside it —
     * is not one either.</p>
     */
    private static boolean hasItemsBesideTheStar(@NotNull PsiElement star, int replacedLength) {
        PsiElement clause = star.getParent();
        TextRange starRange = star.getTextRange();
        if (clause == null || starRange == null) return false;
        TextRange replaced = TextRange.from(starRange.getStartOffset(), replacedLength);
        for (PsiElement item : clause.getChildren()) {
            if (item == star || item.getFirstChild() == null) continue;
            TextRange range = item.getTextRange();
            if (range == null || !replaced.contains(range)) return true;
        }
        return false;
    }
}
