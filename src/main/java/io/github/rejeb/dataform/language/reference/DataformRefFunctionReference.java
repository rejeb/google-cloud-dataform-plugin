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
package io.github.rejeb.dataform.language.reference;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import io.github.rejeb.dataform.language.compilation.DataformCompilationService;
import io.github.rejeb.dataform.language.compilation.model.*;
import io.github.rejeb.dataform.language.completion.DataformActionLookups;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class DataformRefFunctionReference extends PsiReferenceBase<PsiElement> {

    private final ActionReference action;

    public DataformRefFunctionReference(@NotNull PsiElement element,
                                        @NotNull ActionReference action,
                                        @NotNull TextRange range) {
        super(element, range);
        this.action = action;
    }

    @Override
    public @Nullable PsiElement resolve() {
        return ActionFiles.psiFileOf(myElement.getProject(), action);
    }

    @Override
    public Object @NotNull [] getVariants() {
        CompiledGraph graph = DataformCompilationService.getInstance(myElement.getProject()).getCompiledGraph();
        if (graph == null) {
            return EMPTY_ARRAY;
        }
        List<LookupElement> variants = new ArrayList<>(DataformActionLookups.tables(graph));
        variants.addAll(DataformActionLookups.declarations(graph));
        return variants.toArray();
    }
}
