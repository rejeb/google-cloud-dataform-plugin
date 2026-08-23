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

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
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
 * of a SQLX file are injection hosts whose manipulators rebuild the file for each change — one of
 * them rebuilds it wrongly — so going through them would be both slow and unsafe. Ranges of one file
 * are written from the last to the first, so that writing one never moves the next.</p>
 */
public final class ColumnRenameEdits {

    private ColumnRenameEdits() {
    }

    /**
     * Writes the places of {@code usages}, which are the ones the user kept.
     *
     * <p>Must run inside a write action.</p>
     *
     * @return the number of places written
     */
    public static int apply(@NotNull Project project, UsageInfo @NotNull [] usages) {
        Map<VirtualFile, List<ColumnRenameEdit>> byFile = new LinkedHashMap<>();
        for (UsageInfo usage : usages) {
            if (!(usage instanceof ColumnRenameUsageInfo info)) continue;
            byFile.computeIfAbsent(info.edit().file(), file -> new ArrayList<>()).add(info.edit());
        }

        int written = 0;
        PsiDocumentManager documents = PsiDocumentManager.getInstance(project);
        for (Map.Entry<VirtualFile, List<ColumnRenameEdit>> entry : byFile.entrySet()) {
            Document document = FileDocumentManager.getInstance().getDocument(entry.getKey());
            if (document == null) continue;
            List<ColumnRenameEdit> edits = new ArrayList<>(entry.getValue());
            edits.sort(Comparator.comparingInt(edit -> -edit.hostRange().getStartOffset()));
            for (ColumnRenameEdit edit : edits) {
                if (edit.hostRange().getEndOffset() > document.getTextLength()) continue;
                document.replaceString(edit.hostRange().getStartOffset(),
                        edit.hostRange().getEndOffset(), edit.replacement());
                written++;
            }
            documents.commitDocument(document);
        }
        return written;
    }
}
