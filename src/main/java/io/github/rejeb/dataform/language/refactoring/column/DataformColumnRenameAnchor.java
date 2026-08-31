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
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.util.IncorrectOperationException;
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
 * <p>It is never written to: the rename is performed by the processor, which edits documents.</p>
 */
public final class DataformColumnRenameAnchor extends LightElement implements PsiNameIdentifierOwner {

    private final PsiElement identifier;
    private final String columnName;

    public DataformColumnRenameAnchor(@NotNull PsiElement identifier, @NotNull String columnName) {
        super(identifier.getManager(), identifier.getLanguage());
        this.identifier = identifier;
        this.columnName = columnName;
    }

    /** The identifier of the file the caret sits on, which the template renames. */
    @Override
    public @NotNull PsiElement getNameIdentifier() {
        return identifier;
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
        return identifier.getContainingFile();
    }

    @Override
    public TextRange getTextRange() {
        return identifier.getTextRange();
    }

    @Override
    public int getTextOffset() {
        return identifier.getTextOffset();
    }

    @Override
    public int getStartOffsetInParent() {
        return identifier.getStartOffsetInParent();
    }

    @Override
    public PsiElement getParent() {
        return identifier.getParent();
    }

    @Override
    public @NotNull Language getLanguage() {
        return identifier.getLanguage();
    }

    @Override
    public boolean isValid() {
        return identifier.isValid();
    }

    @Override
    public boolean isWritable() {
        return identifier.isWritable();
    }

    @Override
    public String getText() {
        return identifier.getText();
    }

    @Override
    public PsiElement copy() {
        return new DataformColumnRenameAnchor(identifier, columnName);
    }

    @Override
    public void accept(@NotNull PsiElementVisitor visitor) {
        visitor.visitElement(this);
    }

    @Override
    public boolean isEquivalentTo(@Nullable PsiElement another) {
        if (this == another) return true;
        return another instanceof DataformColumnRenameAnchor other
                && identifier.equals(other.identifier);
    }

    @Override
    public String toString() {
        return "DataformColumnRenameAnchor(" + columnName + ")";
    }
}
