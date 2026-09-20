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
package io.github.rejeb.dataform.language.refactoring.column;

import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.sql.psi.SqlCompositeElementTypes;
import com.intellij.util.IncorrectOperationException;
import io.github.rejeb.dataform.language.schema.sql.SqlPsiParts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The element the in-place rename template is anchored on.
 *
 * <p>A Dataform column is not a PSI element of the file: a select-list item naming one is a
 * {@code SqlReferenceExpression}, which is neither a {@code PsiNamedElement} nor a
 * {@code PsiNameIdentifierOwner}, and the identifier inside it is not one either. The in-place
 * refactoring of the platform requires a named element, so this one stands for the column while
 * reporting the file, the range and the validity of the identifier the caret sits on.</p>
 *
 * <p>The identifier is not held: it is found again at each call, from the SQLX file and a range of
 * its document that follows the edits. Starting the template deletes the identifier and inserts it
 * back, and every keystroke of the gesture rewrites it, so the element the caret sat on is gone as
 * soon as the template starts while the platform keeps asking the anchor about it. A smart
 * pointer does not survive this either: its range collapses when the identifier is deleted.</p>
 *
 * <p>It is never written to: the rename is performed by the processor, which edits documents.</p>
 */
public final class DataformColumnRenameAnchor extends LightElement implements PsiNameIdentifierOwner {

    private final PsiFile hostFile;
    private final RangeMarker hostRange;
    private final Language language;
    private final String columnName;

    public DataformColumnRenameAnchor(@NotNull PsiElement identifier, @NotNull String columnName) {
        super(identifier.getManager(), identifier.getLanguage());
        InjectedLanguageManager injections =
                InjectedLanguageManager.getInstance(identifier.getProject());
        PsiFile hostFile = injections.getTopLevelFile(identifier);
        Document hostDocument = hostFile == null
                ? null
                : PsiDocumentManager.getInstance(identifier.getProject()).getDocument(hostFile);
        if (hostDocument == null) {
            throw new IllegalArgumentException("A rename anchor needs an identifier of a document");
        }
        this.hostFile = hostFile;
        this.hostRange = hostDocument.createRangeMarker(
                injections.injectedToHost(identifier, identifier.getTextRange()));
        this.hostRange.setGreedyToLeft(true);
        this.hostRange.setGreedyToRight(true);
        this.language = identifier.getLanguage();
        this.columnName = columnName;
    }

    /**
     * The identifier of the file the caret sits on, as the file holds it now; null once the range
     * holds no identifier any more.
     */
    @Override
    public @Nullable PsiElement getNameIdentifier() {
        if (!hostRange.isValid() || !hostFile.isValid()) return null;
        PsiElement token = InjectedLanguageManager.getInstance(getProject())
                .findInjectedElementAt(hostFile, hostRange.getStartOffset());
        PsiElement identifier = token == null ? null : token.getParent();
        return SqlPsiParts.isType(identifier, SqlCompositeElementTypes.SQL_IDENTIFIER)
                ? identifier
                : null;
    }

    @Override
    public @NotNull String getName() {
        return columnName;
    }

    /**
     * Always refused: the column has no source of its own, and every occurrence of it is written by
     * the rename processor.
     */
    @Override
    public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
        throw new IncorrectOperationException("Dataform columns are renamed by the rename processor");
    }

    @Override
    public PsiFile getContainingFile() {
        PsiElement identifier = getNameIdentifier();
        return identifier == null ? null : identifier.getContainingFile();
    }

    @Override
    public TextRange getTextRange() {
        PsiElement identifier = getNameIdentifier();
        return identifier == null ? null : identifier.getTextRange();
    }

    @Override
    public int getTextOffset() {
        PsiElement identifier = getNameIdentifier();
        return identifier == null ? 0 : identifier.getTextOffset();
    }

    @Override
    public int getStartOffsetInParent() {
        PsiElement identifier = getNameIdentifier();
        return identifier == null ? 0 : identifier.getStartOffsetInParent();
    }

    @Override
    public PsiElement getParent() {
        PsiElement identifier = getNameIdentifier();
        return identifier == null ? null : identifier.getParent();
    }

    @Override
    public @NotNull Language getLanguage() {
        return language;
    }

    @Override
    public boolean isValid() {
        return getNameIdentifier() != null;
    }

    @Override
    public boolean isWritable() {
        return hostFile.isWritable();
    }

    @Override
    public String getText() {
        PsiElement identifier = getNameIdentifier();
        return identifier == null ? columnName : identifier.getText();
    }

    @Override
    public PsiElement copy() {
        PsiElement identifier = getNameIdentifier();
        return identifier == null ? this : new DataformColumnRenameAnchor(identifier, columnName);
    }

    @Override
    public void accept(@NotNull PsiElementVisitor visitor) {
        visitor.visitElement(this);
    }

    @Override
    public boolean isEquivalentTo(@Nullable PsiElement another) {
        if (this == another) return true;
        return another instanceof DataformColumnRenameAnchor other
                && hostFile.equals(other.hostFile)
                && hostRange.getStartOffset() == other.hostRange.getStartOffset();
    }

    @Override
    public String toString() {
        return "DataformColumnRenameAnchor(" + columnName + ")";
    }
}
