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
import com.intellij.psi.PsiElement;
import io.github.rejeb.dataform.language.psi.SqlxJsBlock;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

public class SqlxJsInjector implements MultiHostInjector {
    @Override
    public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar,
                                     @NotNull PsiElement context) {

        if (context instanceof SqlxJsBlock) {
            SqlxJsBlock jsBlock = (SqlxJsBlock) context;
            String text = jsBlock.getText();

            int startBrace = text.indexOf('{');
            int endBrace = text.lastIndexOf('}');

            if (startBrace >= 0 && endBrace > startBrace) {
                registrar.startInjecting(JavascriptLanguage.INSTANCE)
                        .addPlace(
                                null,
                                null,
                                jsBlock,
                                com.intellij.openapi.util.TextRange.create(
                                        startBrace + 1,
                                        endBrace
                                )
                        )
                        .doneInjecting();
            }
        }
    }

    @NotNull
    @Override
    public List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
        return Arrays.asList(SqlxJsBlock.class);
    }
}