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

        System.out.println("=== SqlxConfigInjector ===");
        System.out.println("ConfigBlock text: " + text);
        System.out.println("ConfigBlock range: " + configBlock.getTextRange());

        if (text.isEmpty()) {
            System.out.println("Text is empty, returning");
            return;
        }

        List<TextRange> jsRanges = collectJsElementRanges(configBlock);
        System.out.println("JS ranges collected: " + jsRanges.size());
        for (int i = 0; i < jsRanges.size(); i++) {
            System.out.println("  Range " + i + ": " + jsRanges.get(i));
        }

        if (hasOverlappingRanges(jsRanges)) {
            System.out.println("Overlapping ranges detected! Aborting injection.");
            return;
        }

        if (jsRanges.isEmpty()) {
            System.out.println("No JS ranges, injecting full JSON5");
            registrar.startInjecting(Json5Language.INSTANCE);
            registrar.addPlace(null, null, configBlock, new TextRange(0, text.length()));
            registrar.doneInjecting();
            return;
        }

        System.out.println("Sorting JS ranges");
        jsRanges.sort(Comparator.comparingInt(TextRange::getStartOffset));

        System.out.println("Starting multi-host injection with JS replacement");
        registrar.startInjecting(Json5Language.INSTANCE);

        int currentPos = 0;

        for (int i = 0; i < jsRanges.size(); i++) {
            TextRange jsRange = jsRanges.get(i);
            System.out.println("Processing JS range " + i + ": " + jsRange + ", currentPos: " + currentPos);

            // Inject the part before the JS element
            if (currentPos < jsRange.getStartOffset()) {
                TextRange beforeRange = new TextRange(currentPos, jsRange.getStartOffset());
                String beforeText = text.substring(beforeRange.getStartOffset(), beforeRange.getEndOffset());
                System.out.println("  Injecting part before JS: " + beforeRange);
                System.out.println("  Text: '" + beforeText + "'");
                registrar.addPlace(null, null, configBlock, beforeRange);
            }

            // Replace the JS element with a string placeholder
            String jsText = text.substring(jsRange.getStartOffset(), jsRange.getEndOffset());
            String placeholder = "\"__js_placeholder_" + i + "__\"";
            System.out.println("  Replacing JS element: '" + jsText + "' with: '" + placeholder + "'");
            registrar.addPlace(placeholder, null, configBlock, new TextRange(jsRange.getStartOffset(), jsRange.getStartOffset()));

            currentPos = jsRange.getEndOffset();
            System.out.println("  New currentPos: " + currentPos);
        }

        // Inject the remaining part after the last JS element
        if (currentPos < text.length()) {
            TextRange remainingRange = new TextRange(currentPos, text.length());
            String remainingText = text.substring(remainingRange.getStartOffset(), remainingRange.getEndOffset());
            System.out.println("Injecting final part: " + remainingRange);
            System.out.println("  Text: '" + remainingText + "'");
            registrar.addPlace(null, null, configBlock, remainingRange);
        }

        System.out.println("Done injecting");
        registrar.doneInjecting();
    }

    @NotNull
    @Override
    public List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
        return Collections.singletonList(SqlxConfigBlock.class);
    }

}
