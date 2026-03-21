/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
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

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class SqlxConfigBlockManipulator extends AbstractElementManipulator<SqlxConfigBlock> {

    @Override
    public SqlxConfigBlock handleContentChange(@NotNull SqlxConfigBlock element,
                                               @NotNull TextRange range,
                                               @NotNull String newContent)
            throws IncorrectOperationException {

        PsiElement replace = element.replace(
                element.getContainingFile().copy()
        );
        return replace instanceof SqlxConfigBlock ? (SqlxConfigBlock) replace : element;
    }

    @Override
    public @NotNull TextRange getRangeInElement(@NotNull SqlxConfigBlock element) {
        return TextRange.from(0, element.getTextLength());
    }
}
