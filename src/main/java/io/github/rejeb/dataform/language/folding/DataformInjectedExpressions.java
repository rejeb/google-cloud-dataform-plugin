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
package io.github.rejeb.dataform.language.folding;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import io.github.rejeb.dataform.language.evaluation.DataformExpression;
import io.github.rejeb.dataform.language.evaluation.DataformExpressionCollector;
import io.github.rejeb.dataform.language.psi.SqlxConfigBlock;
import io.github.rejeb.dataform.language.psi.SqlxJsBlock;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Lists the expressions of the JavaScript injected in {@code config} and {@code js} blocks, with their
 * ranges mapped to the host document so they can be folded there.
 *
 * <p>Must be called inside a read action.</p>
 */
public final class DataformInjectedExpressions {

    private DataformInjectedExpressions() {
    }

    /**
     * Returns the injected includes and workflow-settings references of a SQLX file, in host coordinates.
     */
    @NotNull
    public static List<DataformExpression> withHostRanges(@NotNull PsiFile sqlxFile,
                                                          @NotNull Set<String> includeNames) {
        InjectedLanguageManager manager = InjectedLanguageManager.getInstance(sqlxFile.getProject());
        List<DataformExpression> expressions = new ArrayList<>();
        for (PsiElement block : PsiTreeUtil.findChildrenOfAnyType(sqlxFile, SqlxConfigBlock.class, SqlxJsBlock.class)) {
            manager.enumerate(block, (injectedFile, places) -> {
                collect(manager, DataformExpressionCollector
                        .collectIncludesReferenceElements(injectedFile, includeNames), expressions);
                collect(manager, DataformExpressionCollector
                        .collectWorkflowSettingsReferenceElements(injectedFile), expressions);
            });
        }
        return expressions;
    }

    private static void collect(@NotNull InjectedLanguageManager manager,
                                @NotNull List<DataformExpressionCollector.FoldablePart> injected,
                                @NotNull List<DataformExpression> target) {
        injected.forEach(part -> target.add(new DataformExpression(
                part.expression().source(),
                part.expression().hostText(),
                manager.injectedToHost(part.element(), part.expression().hostRange()),
                part.expression().kind())));
    }
}
