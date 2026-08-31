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
package io.github.rejeb.dataform.language.refactoring.column.apply;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.usageView.UsageInfo;
import io.github.rejeb.dataform.language.refactoring.column.usage.ColumnRenameEdit;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes the places of a rename.
 *
 * <p>Every place is written as a range of the host document rather than through the PSI. The blocks
 * of a SQLX file are injection hosts whose manipulators rebuild the block for each change, so going
 * through them would be both slow and, for a rename touching a file dozens of times, unsafe. Ranges
 * of one file are written from the last to the first, so that writing one never moves the next.</p>
 *
 * <p>Where to write is asked of each place again here, and never taken from the offsets the plan
 * recorded: a plan is reviewed in the rename window, which the user may leave open while typing
 * somewhere else. Every range is taken before the first character is written, so they all describe
 * the same file.</p>
 */
public final class ColumnRenameEdits {

    private static final Logger LOG = Logger.getInstance(ColumnRenameEdits.class);

    private ColumnRenameEdits() {
    }

    /**
     * One place to write, at the range it occupies now.
     *
     * @param range       the range of the host document to replace
     * @param replacement the text written in its place
     */
    private record Write(@NotNull TextRange range, @NotNull String replacement) {
    }

    /**
     * Writes the places of {@code usages}, which are the ones the user kept. A place whose element
     * is gone is left out rather than written at a guessed offset.
     *
     * <p>Must run inside a write action.</p>
     *
     * @return the number of places written
     */
    public static int apply(@NotNull Project project, UsageInfo @NotNull [] usages) {
        Map<VirtualFile, List<Write>> byFile = new LinkedHashMap<>();
        for (UsageInfo usage : usages) {
            if (!(usage instanceof ColumnRenameUsageInfo info)) continue;
            ColumnRenameEdit edit = info.edit();
            TextRange range = edit.currentRange();
            if (range == null) {
                LOG.warn("The place of " + edit.presentation() + " in " + edit.file().getName()
                        + " is gone since the rename was planned, so it keeps the old name");
                continue;
            }
            byFile.computeIfAbsent(edit.file(), file -> new ArrayList<>())
                    .add(new Write(range, edit.replacement()));
        }

        int written = 0;
        PsiDocumentManager documents = PsiDocumentManager.getInstance(project);
        for (Map.Entry<VirtualFile, List<Write>> entry : byFile.entrySet()) {
            Document document = FileDocumentManager.getInstance().getDocument(entry.getKey());
            if (document == null) continue;
            List<Write> writes = new ArrayList<>(entry.getValue());
            writes.sort(Comparator.comparingInt(write -> -write.range().getStartOffset()));
            for (Write write : writes) {
                if (write.range().getEndOffset() > document.getTextLength()) continue;
                document.replaceString(write.range().getStartOffset(),
                        write.range().getEndOffset(), write.replacement());
                written++;
            }
            documents.commitDocument(document);
        }
        return written;
    }
}
