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
package io.github.rejeb.dataform.language.refactoring.column.plan;

import com.intellij.openapi.vfs.VirtualFile;
import io.github.rejeb.dataform.language.lineage.column.ColumnRef;
import io.github.rejeb.dataform.language.refactoring.column.target.ColumnRenameSubject;
import io.github.rejeb.dataform.language.refactoring.column.usage.ColumnRenameEdit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Everything a rename is going to do, decided before anything is written.
 *
 * @param subject       the column the gesture started on
 * @param newName       the name every column of the plan takes
 * @param columns       the columns the rename reaches
 * @param edits         the places to write
 * @param starBoundaries the columns of the plan that have no declaration to rewrite
 * @param warnings      what the user should know before applying
 * @param refusal       why the rename cannot run at all, {@code null} when it can
 */
public record ColumnRenamePlan(@NotNull ColumnRenameSubject subject,
                               @NotNull String newName,
                               @NotNull Set<ColumnRef> columns,
                               @NotNull List<ColumnRenameEdit> edits,
                               @NotNull List<StarBoundary> starBoundaries,
                               @NotNull List<String> warnings,
                               @Nullable String refusal) {

    /**
     * Drops any place overlapping one already kept, so that writing the plan can never write over
     * what it has just written.
     *
     * <p>Two collectors can reach the same characters from different sides — the string a helper is
     * handed is both a literal and a piece of the text around it — and writing both leaves the file
     * mangled. The place found by resolving wins over the one found by matching text; between two of
     * the same kind, the wider one wins, since it is the one that carries its own quoting.</p>
     */
    public ColumnRenamePlan {
        edits = withoutOverlaps(edits);
    }

    private static @NotNull List<ColumnRenameEdit> withoutOverlaps(
            @NotNull List<ColumnRenameEdit> edits) {
        List<ColumnRenameEdit> ordered = new ArrayList<>(edits);
        ordered.sort(Comparator
                .comparing((ColumnRenameEdit edit) -> edit.file().getPath())
                .thenComparing(edit -> edit.hostRange().getStartOffset())
                .thenComparing(edit -> edit.risk() == ColumnRenameEdit.Risk.CERTAIN ? 0 : 1)
                .thenComparing(edit -> -edit.hostRange().getLength()));

        List<ColumnRenameEdit> kept = new ArrayList<>();
        for (ColumnRenameEdit edit : ordered) {
            boolean overlaps = kept.stream().anyMatch(other -> other.file().equals(edit.file())
                    && other.hostRange().intersects(edit.hostRange()));
            if (!overlaps) kept.add(edit);
        }
        return List.copyOf(kept);
    }

    /** A plan that cannot run, carrying the reason to show the user. */
    public static @NotNull ColumnRenamePlan refused(@NotNull ColumnRenameSubject subject,
                                                    @NotNull String newName,
                                                    @NotNull String reason) {
        return new ColumnRenamePlan(subject, newName, Set.of(), List.of(), List.of(), List.of(),
                reason);
    }

    /** Whether the plan can be applied. */
    public boolean isRunnable() {
        return refusal == null && !edits.isEmpty();
    }

    /** Whether the user still has to say what to do about a star. */
    public boolean needsStarDecision() {
        return refusal == null && !starBoundaries.isEmpty();
    }

    /**
     * Whether the places must be reviewed before they are written. A place found by matching text
     * and a plan that could not be fully determined are both things the user has to see.
     */
    public boolean needsPreview() {
        return !warnings.isEmpty()
                || edits.stream().anyMatch(edit -> edit.risk() == ColumnRenameEdit.Risk.HEURISTIC);
    }

    /** The edits of one file, in the order they were collected. */
    public @NotNull List<ColumnRenameEdit> editsIn(@NotNull VirtualFile file) {
        return edits.stream().filter(edit -> file.equals(edit.file())).toList();
    }
}
