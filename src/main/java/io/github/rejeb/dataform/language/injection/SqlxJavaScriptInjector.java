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
package io.github.rejeb.dataform.language.injection;

import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.lang.javascript.JavascriptLanguage;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import io.github.rejeb.dataform.language.psi.SqlxConfigBlock;
import io.github.rejeb.dataform.language.psi.SqlxJsBlock;
import io.github.rejeb.dataform.language.psi.SqlxPsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Injects JavaScript into the {@code config} block, typed as a {@code DataformConfig} object literal,
 * and into the {@code js} block, wrapped in a function body.
 */
public class SqlxJavaScriptInjector implements MultiHostInjector {

    @Override
    public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar,
                                     @NotNull PsiElement context) {
        int length = context.getTextLength();
        if (length == 0) {
            return;
        }
        if (context instanceof SqlxConfigBlock config) {
            inject(registrar, config, "(/** @type {DataformConfig} */({", "}))", length);
        } else if (context instanceof SqlxJsBlock js) {
            inject(registrar, js, "function my_function() {", "}", length);
        }
    }

    @NotNull
    @Override
    public List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
        return List.of(SqlxConfigBlock.class, SqlxJsBlock.class);
    }

    private static void inject(@NotNull MultiHostRegistrar registrar,
                               @NotNull SqlxPsiElement host,
                               @NotNull String prefix,
                               @NotNull String suffix,
                               int length) {
        registrar.startInjecting(JavascriptLanguage.INSTANCE)
                .addPlace(prefix, suffix, host, new TextRange(0, length))
                .doneInjecting();
    }
}
