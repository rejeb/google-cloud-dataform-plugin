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
package io.github.rejeb.dataform.language.diagnostics.sql.fix;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import io.github.rejeb.dataform.language.diagnostics.sql.hint.SqlFix;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Applies a {@link SqlFix} to the SQLX file it was offered for. The fix names host offsets, so the
 * text is edited on the SQLX document itself, whichever injected fragment the error was found in.
 * The text the fix replaces is remembered and checked first: a document edited since the fix was
 * offered is left alone.
 */
public final class ApplySqlFixAction extends PsiUpdateModCommandAction<PsiFile> {

    private final SqlFix fix;
    private final String replaced;

    public ApplySqlFixAction(@NotNull PsiFile hostFile, @NotNull SqlFix fix) {
        super(hostFile);
        this.fix = fix;
        String text = hostFile.getText();
        this.replaced = fix.range().getEndOffset() <= text.length() ? fix.range().substring(text) : "";
    }

    @Override
    public @NotNull String getFamilyName() {
        return "Fix SQL error";
    }

    @Override
    protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiFile file) {
        return Presentation.of(fix.label());
    }

    @Override
    protected void invoke(@NotNull ActionContext context, @NotNull PsiFile file, @NotNull ModPsiUpdater updater) {
        Document document = file.getFileDocument();
        TextRange range = fix.range();
        if (range.getEndOffset() > document.getTextLength()) return;
        if (!document.getText(range).equals(replaced)) return;
        document.replaceString(range.getStartOffset(), range.getEndOffset(), fix.replacement());
    }
}
