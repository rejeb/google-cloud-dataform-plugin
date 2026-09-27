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

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.javascript.psi.JSLiteralExpression;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import io.github.rejeb.dataform.language.compilation.model.ActionReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Takes Ctrl+Click on the name given to {@code ref()} or {@code resolve()} to the action it designates.
 *
 * <p>The contributed reference of the name is not enough: a literal inside a SQL template string of a
 * JavaScript definition file exposes no contributed reference.</p>
 */
public final class DataformRefGotoDeclarationHandler implements GotoDeclarationHandler {

    @Override
    public PsiElement @Nullable [] getGotoDeclarationTargets(@Nullable PsiElement sourceElement,
                                                             int offset,
                                                             @Nullable Editor editor) {
        if (sourceElement == null) {
            return null;
        }
        JSLiteralExpression name = nameLiteral(sourceElement);
        if (name == null && sourceElement.getContainingFile() != null) {
            PsiElement injected = InjectedLanguageManager.getInstance(sourceElement.getProject())
                    .findInjectedElementAt(sourceElement.getContainingFile(), offset);
            name = injected == null ? null : nameLiteral(injected);
        }
        if (name == null) {
            return null;
        }
        Optional<ActionReference> action = RefCallLiterals.designatedBy(name);
        if (action.isEmpty()) {
            return null;
        }
        PsiElement target = new DataformRefFunctionReference(
                name, action.get(), new TextRange(1, name.getTextLength() - 1)).resolve();
        return target == null ? null : new PsiElement[]{target};
    }

    @Nullable
    private static JSLiteralExpression nameLiteral(@NotNull PsiElement element) {
        return PsiTreeUtil.getParentOfType(element, JSLiteralExpression.class, false);
    }
}
