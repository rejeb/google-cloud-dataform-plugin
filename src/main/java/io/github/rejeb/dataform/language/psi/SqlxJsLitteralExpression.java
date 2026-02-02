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
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.LiteralTextEscaper;
import com.intellij.psi.PsiLanguageInjectionHost;
import org.jetbrains.annotations.NotNull;

public class SqlxJsLitteralExpression extends ASTWrapperPsiElement implements PsiLanguageInjectionHost {

    public SqlxJsLitteralExpression(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    public boolean isValidHost() {
        return true;
    }

    @Override
    public PsiLanguageInjectionHost updateText(@NotNull String text) {
        return this;
    }

    @NotNull
    @Override
    public LiteralTextEscaper<? extends PsiLanguageInjectionHost> createLiteralTextEscaper() {
        return new LiteralTextEscaper<>(this) {

            @Override
            public boolean decode(@NotNull TextRange rangeInsideHost,
                                  @NotNull StringBuilder outChars) {
                String text = myHost.getText();

                if (rangeInsideHost.getStartOffset() < 0 ||
                        rangeInsideHost.getEndOffset() > text.length()) {
                    System.err.println("Invalid range for decode: " + rangeInsideHost +
                            " for text length: " + text.length());
                    return false;
                }

                String substring = rangeInsideHost.substring(text);
                outChars.append(substring);

                return true;
            }

            @Override
            public int getOffsetInHost(int offsetInDecoded,
                                       @NotNull TextRange rangeInsideHost) {
                int result = offsetInDecoded + rangeInsideHost.getStartOffset();

                if (result < 0 || result > myHost.getTextLength()) {
                    System.err.println("Invalid offset calculation: " + result +
                            " for text length: " + myHost.getTextLength());
                    return rangeInsideHost.getStartOffset();
                }

                return result;
            }

            @NotNull
            @Override
            public TextRange getRelevantTextRange() {
                String text = myHost.getText();

                if (text.startsWith("${") && text.endsWith("}") && text.length() > 3) {
                    return new TextRange(2, text.length() - 1);
                }

                return new TextRange(0, text.length());
            }

            @Override
            public boolean isOneLine() {
                return true;
            }
        };
    }

}
