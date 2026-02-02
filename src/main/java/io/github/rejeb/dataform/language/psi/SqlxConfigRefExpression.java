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
import com.intellij.psi.PsiReference;
import io.github.rejeb.dataform.language.reference.DataformWorkflowSettingsReference;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class SqlxConfigRefExpression extends ASTWrapperPsiElement implements PsiLanguageInjectionHost {

    public SqlxConfigRefExpression(@NotNull ASTNode node) {
        super(node);
    }

    @NotNull
    @Override
    public PsiReference[] getReferences() {
        String text = getText();


        if (!text.startsWith("$")) {
            return PsiReference.EMPTY_ARRAY;
        }

        List<PsiReference> references = new ArrayList<>();



        references.addAll(createReferencesForPath(text));

        return references.toArray(new PsiReference[0]);
    }

    private List<PsiReference> createReferencesForPath(String text) {
        List<PsiReference> references = new ArrayList<>();


        if (!text.startsWith("$")) {
            return references;
        }

        String pathWithoutDollar = text.substring(1);
        if (pathWithoutDollar.isEmpty()) {
            return references;
        }


        String[] parts = pathWithoutDollar.split("\\.");
        int currentOffset = 1;

        StringBuilder currentPath = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty()) {
                continue;
            }

            if (i > 0) {
                currentPath.append(".");
            }
            currentPath.append(part);


            int startOffset = currentOffset;
            int endOffset = startOffset + part.length();

            TextRange range = new TextRange(startOffset, endOffset);


            references.add(new DataformWorkflowSettingsReference(
                    this,
                    currentPath.toString(),
                    range
            ));

            currentOffset = endOffset + 1;
        }

        return references;
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

                if (text.startsWith("$") && ! text.startsWith("${") && text.endsWith("}") && text.length() > 3) {
                    return new TextRange(1, text.length() - 1);
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
