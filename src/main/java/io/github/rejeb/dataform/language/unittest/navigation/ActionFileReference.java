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
package io.github.rejeb.dataform.language.unittest.navigation;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import io.github.rejeb.dataform.language.compilation.model.ActionReference;
import io.github.rejeb.dataform.language.reference.ActionFiles;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The file of the action a unit test names: the table it tests, or the table an input mocks.
 *
 * <p>Renaming the file the reference points at leaves the name alone: the name of an action is not
 * the name of its file, and writing a file name into the test would break it.</p>
 */
public final class ActionFileReference extends PsiReferenceBase<PsiElement> {

    private final ActionReference action;

    public ActionFileReference(@NotNull PsiElement element,
                               @NotNull TextRange rangeInElement,
                               @NotNull ActionReference action) {
        super(element, rangeInElement, true);
        this.action = action;
    }

    @Override
    public @Nullable PsiElement resolve() {
        return ActionFiles.psiFileOf(myElement.getProject(), action);
    }

    @Override
    public PsiElement handleElementRename(@NotNull String newElementName) {
        return myElement;
    }
}
