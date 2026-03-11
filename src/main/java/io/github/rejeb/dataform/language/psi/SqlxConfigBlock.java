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
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.LiteralTextEscaper;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

public class SqlxConfigBlock extends ASTWrapperPsiElement implements PsiLanguageInjectionHost {

    public SqlxConfigBlock(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    public boolean isValidHost() {
        return true;
    }

    @Override
    public PsiLanguageInjectionHost updateText(@NotNull String text) {
        return ElementManipulators.handleContentChange(this, text);
    }

    @NotNull
    @Override
    public LiteralTextEscaper<? extends PsiLanguageInjectionHost> createLiteralTextEscaper() {
        return new LiteralTextEscaper<>(this) {

            @Override
            public boolean decode(@NotNull TextRange rangeInsideHost, @NotNull StringBuilder outChars) {
                String fullText = myHost.getText();
                String sub = rangeInsideHost.substring(fullText);

                StringBuilder replaced = new StringBuilder(sub);
                int blockStart = rangeInsideHost.getStartOffset();

                PsiTreeUtil.processElements(myHost, element -> {
                    IElementType type = element.getNode().getElementType();
                    if (type == SqlxElementTypes.JS_LITERAL_ELEMENT
                            || type == SqlxElementTypes.TEMPLATE_EXPRESSION_ELEMENT) {
                        TextRange abs = element.getTextRange();
                        int relStart = abs.getStartOffset() - myHost.getTextRange().getStartOffset() - blockStart;
                        int relEnd = abs.getEndOffset() - myHost.getTextRange().getStartOffset() - blockStart;
                        if (relStart >= 0 && relEnd <= replaced.length() && relStart < relEnd) {
                            int len = relEnd - relStart;
                            String placeholder = buildPlaceholder(len);
                            replaced.replace(relStart, relEnd, placeholder);
                        }
                    }
                    return true;
                });

                outChars.append(replaced);
                return true;
            }

            @Override
            public int getOffsetInHost(int offsetInDecoded, @NotNull TextRange rangeInsideHost) {
                return rangeInsideHost.getStartOffset() + offsetInDecoded;
            }

            @Override
            public @NotNull TextRange getRelevantTextRange() {
                return new TextRange(0, myHost.getTextLength());
            }

            @Override
            public boolean isOneLine() {
                return false;
            }

            private String buildPlaceholder(int len) {
                if (len < 2) return "\"\"".substring(0, len);
                StringBuilder sb = new StringBuilder(len);
                sb.append('"');
                for (int i = 1; i < len - 1; i++) sb.append('x');
                sb.append('"');
                return sb.toString();
            }
        };
    }
}
