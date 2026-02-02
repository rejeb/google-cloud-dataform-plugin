/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
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

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.LiteralTextEscaper;
import com.intellij.psi.PsiLanguageInjectionHost;
import org.jetbrains.annotations.NotNull;

public class SqlxPsiElement  extends ASTWrapperPsiElement
        implements PsiLanguageInjectionHost {

    public SqlxPsiElement(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    public boolean isValidHost() {
        return true;
    }

    @Override
    public SqlxPsiElement updateText(@NotNull String text) {
        return this;
    }

    @NotNull
    @Override
    public LiteralTextEscaper<? extends PsiLanguageInjectionHost> createLiteralTextEscaper() {
        return new LiteralTextEscaper<SqlxPsiElement>(this) {
            @Override
            public boolean decode(@NotNull com.intellij.openapi.util.TextRange rangeInsideHost,
                                  @NotNull StringBuilder outChars) {
                outChars.append(myHost.getText());
                return true;
            }

            @Override
            public int getOffsetInHost(int offsetInDecoded,
                                       @NotNull com.intellij.openapi.util.TextRange rangeInsideHost) {
                return rangeInsideHost.getStartOffset() + offsetInDecoded;
            }

            @NotNull
            @Override
            public com.intellij.openapi.util.TextRange getRelevantTextRange() {
                return com.intellij.openapi.util.TextRange.from(0, myHost.getTextLength());
            }

            @Override
            public boolean isOneLine() {
                return false;
            }
        };
    }
}