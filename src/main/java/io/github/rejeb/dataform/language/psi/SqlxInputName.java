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
package io.github.rejeb.dataform.language.psi;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiReference;
import io.github.rejeb.dataform.language.compilation.model.ActionReference;
import io.github.rejeb.dataform.language.unittest.navigation.ActionFileReference;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A quoted part of the label of an {@code input} block of a unit test. The last part names the
 * action the input mocks and refers to its file; the parts before it are its schema and database.
 */
public class SqlxInputName extends SqlxPsiElement {

    public SqlxInputName(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    public PsiReference @NotNull [] getReferences() {
        if (!(getParent() instanceof SqlxInputBlock input) || getTextLength() < 2) {
            return PsiReference.EMPTY_ARRAY;
        }
        List<String> parts = input.labelParts();
        SqlxInputName last = null;
        for (var child = input.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof SqlxInputName name) {
                last = name;
            }
        }
        if (last != this || parts.isEmpty() || parts.getLast().isBlank()) {
            return PsiReference.EMPTY_ARRAY;
        }
        ActionReference action = switch (parts.size()) {
            case 1 -> new ActionReference(null, null, parts.get(0));
            case 2 -> new ActionReference(null, parts.get(0), parts.get(1));
            default -> new ActionReference(parts.get(parts.size() - 3), parts.get(parts.size() - 2), parts.getLast());
        };
        return new PsiReference[]{new ActionFileReference(this, new TextRange(1, getTextLength() - 1), action)};
    }

    @Override
    public PsiReference getReference() {
        PsiReference[] references = getReferences();
        return references.length == 0 ? null : references[0];
    }
}
