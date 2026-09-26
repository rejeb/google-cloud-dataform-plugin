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
package io.github.rejeb.dataform.language.diagnostics.sql.mapping;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import io.github.rejeb.dataform.language.injection.InjectionHelper;
import io.github.rejeb.dataform.language.psi.SharedTokenTypes;
import io.github.rejeb.dataform.language.psi.SqlxSqlBlock;
import io.github.rejeb.dataform.language.schema.sql.DryRunQueryText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The text of the SQLX blocks a compiled source is made from, in document order.
 *
 * <p>Dataform compiles everything of a SQLX file outside its config, js and operation blocks into
 * the main query, and each pre_operations block into the pre-operations. This is the SQLX side of
 * the same source: the blocks one after the other, with the host offset of each character and the
 * {@code ${…}} holes they hold.</p>
 */
public final class SqlxSourceText {

    private final String text;
    private final int[] starts;
    private final int[] hostStarts;
    private final List<TextRange> holes;

    private SqlxSourceText(@NotNull String text,
                           int @NotNull [] starts,
                           int @NotNull [] hostStarts,
                           @NotNull List<TextRange> holes) {
        this.text = text;
        this.starts = starts;
        this.hostStarts = hostStarts;
        this.holes = List.copyOf(holes);
    }

    /**
     * The SQLX side of a source of the dry-run, or {@code null} when the file has no block of it.
     */
    public static @Nullable SqlxSourceText of(@NotNull PsiFile hostFile, @NotNull String source) {
        IElementType type = switch (source) {
            case DryRunQueryText.MAIN_QUERY -> SharedTokenTypes.SQL_CONTENT;
            case DryRunQueryText.PRE_OPERATIONS -> SharedTokenTypes.PRE_OPERATIONS_CONTENT;
            default -> null;
        };
        if (type == null) return null;
        List<SqlxSqlBlock> blocks = new ArrayList<>();
        for (SqlxSqlBlock block : PsiTreeUtil.findChildrenOfType(hostFile, SqlxSqlBlock.class)) {
            if (block.getNode().getElementType() == type) blocks.add(block);
        }
        if (blocks.isEmpty()) return null;
        blocks.sort(Comparator.comparingInt(block -> block.getTextRange().getStartOffset()));
        StringBuilder text = new StringBuilder();
        int[] starts = new int[blocks.size()];
        int[] hostStarts = new int[blocks.size()];
        List<TextRange> holes = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
            SqlxSqlBlock block = blocks.get(i);
            starts[i] = text.length();
            hostStarts[i] = block.getTextRange().getStartOffset();
            text.append(block.getText());
            holes.addAll(InjectionHelper.collectJsElements(block, 0).keySet());
        }
        return new SqlxSourceText(text.toString(), starts, hostStarts, holes);
    }

    /**
     * The blocks, one after the other.
     */
    public @NotNull String text() {
        return text;
    }

    /**
     * The host offset of an offset of {@link #text()}.
     */
    public int toHost(int offset) {
        int block = 0;
        for (int i = 0; i < starts.length && starts[i] <= offset; i++) block = i;
        return hostStarts[block] + offset - starts[block];
    }

    /**
     * The template hole holding a host offset, or {@code null}.
     */
    public @Nullable TextRange holeAt(int hostOffset) {
        for (TextRange hole : holes) {
            if (hole.getStartOffset() <= hostOffset && hostOffset < hole.getEndOffset()) return hole;
        }
        return null;
    }

    /**
     * The first template hole a host range overlaps, or {@code null}. An empty range overlaps the
     * hole it sits strictly inside of.
     */
    public @Nullable TextRange holeTouching(@NotNull TextRange hostRange) {
        if (hostRange.isEmpty()) {
            TextRange hole = holeAt(hostRange.getStartOffset());
            return hole != null && hole.getStartOffset() < hostRange.getStartOffset() ? hole : null;
        }
        for (TextRange hole : holes) {
            if (hole.intersectsStrict(hostRange)) return hole;
        }
        return null;
    }
}
