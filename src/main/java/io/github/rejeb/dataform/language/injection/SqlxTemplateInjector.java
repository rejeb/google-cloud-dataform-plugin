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
package io.github.rejeb.dataform.language.injection;

import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.lang.javascript.JavascriptLanguage;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import io.github.rejeb.dataform.language.psi.SqlxJsLitteralExpression;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class SqlxTemplateInjector implements MultiHostInjector {

    @Override
    public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar,
                                     @NotNull PsiElement context) {


        String text = context.getText();
        int startOffset = 0;
        int endOffset = text.length();

        if (text.startsWith("${") && text.endsWith("}")) {
            startOffset = 2;
            endOffset = text.length() - 1;
        }

        if (endOffset > startOffset) {
            TextRange injectionRange = new TextRange(startOffset, endOffset);
            registrar.startInjecting(JavascriptLanguage.INSTANCE);
            registrar.addPlace(null, null, (PsiLanguageInjectionHost) context, injectionRange);
            registrar.doneInjecting();
        }
    }

    @NotNull
    @Override
    public List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
        return List.of(SqlxJsLitteralExpression.class);
    }
}
