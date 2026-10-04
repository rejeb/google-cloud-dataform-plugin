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
package io.github.rejeb.dataform.language.schema.sql.model;

import com.intellij.database.Dbms;
import com.intellij.database.model.DasObject;
import com.intellij.database.symbols.DasSymbol;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.sql.dialects.bigquery.BigQueryDialect;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

/**
 * A BigQuery object derived from the compiled Dataform graph, offered to the SQL support as its own
 * declaration. It is read-only: it has no source of its own to edit.
 */
public abstract class DataformDasElement extends LightElement implements DasObject, DasSymbol, PsiNamedElement {

    protected DataformDasElement(@NotNull PsiManager psiManager) {
        super(psiManager, BigQueryDialect.INSTANCE);
    }

    @Override
    public abstract @NotNull String getName();

    /**
     * The location shown next to the name in lists, {@code null} for none.
     */
    protected abstract @Nullable String presentableLocation();

    /**
     * Always refused: the object is derived from the compiled graph.
     */
    @Override
    public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
        throw new IncorrectOperationException("Dataform schema objects cannot be renamed");
    }

    @Override
    public @NotNull String toString() {
        return getName();
    }

    @Override
    public @NotNull Dbms getDbms() {
        return Dbms.BIGQUERY;
    }

    @Override
    public @Nullable DasObject getDasObject() {
        return this;
    }

    @Override
    public @NotNull JBIterable<? extends PsiElement> getPsiDeclarations() {
        return JBIterable.of(this);
    }

    @Override
    public @Nullable PsiElement getContextElement() {
        return this;
    }

    @Override
    public boolean isQuoted() {
        return false;
    }

    @Override
    public ItemPresentation getPresentation() {
        return new ItemPresentation() {
            @Override
            public String getPresentableText() {
                return getName();
            }

            @Override
            public String getLocationString() {
                return presentableLocation();
            }

            @Override
            public Icon getIcon(boolean unused) {
                return null;
            }
        };
    }

    @Override
    public boolean canNavigateToSource() {
        return canNavigate();
    }
}
