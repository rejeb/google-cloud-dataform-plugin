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
package io.github.rejeb.dataform.language.service;

import com.intellij.lang.javascript.psi.JSFunction;
import com.intellij.lang.javascript.psi.JSParameterList;
import com.intellij.lang.javascript.psi.JSType;
import com.intellij.lang.javascript.psi.jsdoc.JSDocComment;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

public record DataformFunctionCompletionObject(String name, String signature, String description) {

    public static Optional<DataformFunctionCompletionObject> fromJSFunction(JSFunction function) {
        return Optional.ofNullable(function.getName()).filter(name -> !name.isEmpty())
                .map(name -> new DataformFunctionCompletionObject(name, buildSignature(function), extractJsDoc(function)));
    }

    private static String buildSignature(JSFunction function) {
        JSParameterList parameterList = function.getParameterList();
        String parameters = parameterList == null ? "" : Arrays.stream(parameterList.getParameterVariables())
                .map(param -> param.getName() == null ? ""
                        : param.getName() + typeSuffix(param.getJSType()) + (param.isOptional() ? "?" : ""))
                .collect(Collectors.joining(", "));
        return "(" + parameters + ")" + typeSuffix(function.getReturnType());
    }

    private static String typeSuffix(@Nullable JSType type) {
        return type == null || type.getTypeText().isEmpty() ? "" : ": " + type.getTypeText();
    }

    private static String extractJsDoc(JSFunction function) {
        if (function.getFirstChild() instanceof JSDocComment docComment) {
            return cleanJsDoc(docComment.getText());
        }
        PsiElement prev = function.getPrevSibling();
        while (prev != null) {
            if (prev instanceof PsiComment comment) {
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
