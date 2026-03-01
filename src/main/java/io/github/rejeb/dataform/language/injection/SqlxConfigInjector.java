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

import com.intellij.json.json5.Json5Language;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import io.github.rejeb.dataform.language.psi.SqlxConfigBlock;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static io.github.rejeb.dataform.language.injection.InjectionHelper.collectJsElementRanges;
import static io.github.rejeb.dataform.language.injection.InjectionHelper.hasOverlappingRanges;

public class SqlxConfigInjector implements MultiHostInjector {


    @Override
    public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context) {
        if (!(context instanceof SqlxConfigBlock configBlock)) {
            return;
        }

        String text = configBlock.getText();

        if (text.isEmpty()) {
            return;
        }

        List<TextRange> jsRanges = collectJsElementRanges(configBlock);
        if (hasOverlappingRanges(jsRanges)) {
            return;
        }

        if (jsRanges.isEmpty()) {
            registrar.startInjecting(Json5Language.INSTANCE);
            registrar.addPlace(null, null, configBlock, new TextRange(0, text.length()));
            registrar.doneInjecting();
            return;
        }

        jsRanges.sort(Comparator.comparingInt(TextRange::getStartOffset));

        registrar.startInjecting(Json5Language.INSTANCE);

        int currentPos = 0;

        for (int i = 0; i < jsRanges.size(); i++) {
            TextRange jsRange = jsRanges.get(i);

            // Inject the part before the JS element
            if (currentPos < jsRange.getStartOffset()) {
                TextRange beforeRange = new TextRange(currentPos, jsRange.getStartOffset());
                registrar.addPlace(null, null, configBlock, beforeRange);
            }

            String placeholder = "\"__js_placeholder_" + i + "__\"";
            registrar.addPlace(placeholder, null, configBlock, new TextRange(jsRange.getStartOffset(), jsRange.getStartOffset()));

            currentPos = jsRange.getEndOffset();
        }

        if (currentPos < text.length()) {
            TextRange remainingRange = new TextRange(currentPos, text.length());
            registrar.addPlace(null, null, configBlock, remainingRange);
        }

        registrar.doneInjecting();
    }

    @NotNull
    @Override
    public List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
        return Collections.singletonList(SqlxConfigBlock.class);
    }

}
