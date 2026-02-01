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
package io.github.rejeb.dataform.language.service;

import com.intellij.lang.javascript.psi.JSFunction;
import com.intellij.lang.javascript.psi.JSParameter;
import com.intellij.lang.javascript.psi.JSParameterList;
import com.intellij.lang.javascript.psi.JSType;
import com.intellij.lang.javascript.psi.jsdoc.JSDocComment;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;

import java.util.Optional;

public record DataformFunctionCompletionObject(String name, String signature, String description) {

    public static Optional<DataformFunctionCompletionObject> fromJSFunction(JSFunction function) {
        String name = function.getName();
        if (name != null && !name.isEmpty()) {
            String signature = buildSignature(function);
            String description = extractJsDoc(function);

            return Optional.of(new DataformFunctionCompletionObject(name, signature, description));
        } else {
            return Optional.empty();
        }
    }

    private static String buildSignature(JSFunction function) {
        StringBuilder signature = new StringBuilder("(");
        JSParameterList parameterList = function.getParameterList();
        if (parameterList != null) {
            JSParameter[] parameters = parameterList.getParameterVariables();

            for (int i = 0; i < parameters.length; i++) {
                if (i > 0) signature.append(", ");
                JSParameter param = parameters[i];
                String paramName = param.getName();
                if (paramName != null) {
                    signature.append(paramName);
                    JSType type = param.getJSType();
                    if (type != null) {
                        String typeText = type.getTypeText();
                        if (!typeText.isEmpty()) {
                            signature.append(": ").append(typeText);
                        }
                    }
                    if (param.isOptional()) {
                        signature.append("?");
                    }
                }
            }
        }
        signature.append(")");
        JSType returnType = function.getReturnType();
        if (returnType != null) {
            String returnTypeText = returnType.getTypeText();
            if (!returnTypeText.isEmpty()) {
                signature.append(": ").append(returnTypeText);
            }
        }
        return signature.toString();
    }

    private static String extractJsDoc(JSFunction function) {
        PsiElement prev = function.getPrevSibling();
        while (prev != null) {
            if (prev instanceof JSDocComment) {
                JSDocComment docComment = (JSDocComment) prev;
                return cleanJsDoc(docComment.getText());
            }
            if (prev instanceof PsiComment) {
                PsiComment comment = (PsiComment) prev;
                return cleanJsDoc(comment.getText());
            }
            if (!prev.getText().trim().isEmpty()) {
                break;
            }
            prev = prev.getPrevSibling();
        }

        return "";
    }

    private static String cleanJsDoc(String text) {
        if (text == null) return "";

        return text
                .replaceAll("/\\*\\*", "")
                .replaceAll("\\*/", "")
                .replaceAll("\\s*\\*\\s*", " ")
                .replaceAll("@param.*", "")
                .replaceAll("@returns?.*", "")
                .trim();
    }
}
