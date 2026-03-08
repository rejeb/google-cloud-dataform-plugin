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
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.sql.dialects.bigquery.BigQueryDialect;
import io.github.rejeb.dataform.language.psi.SqlxSqlBlock;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Collections;

import static io.github.rejeb.dataform.language.injection.InjectionHelper.collectJsElements;
import static io.github.rejeb.dataform.language.injection.InjectionHelper.hasOverlappingRanges;

public class SqlxSqlInjector implements MultiHostInjector {

    @Override
    public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar,
                                     @NotNull PsiElement context) {

        if (!(context instanceof SqlxSqlBlock sqlBlock)) return;

        String text = sqlBlock.getText();
        if (text == null || text.isEmpty()) return;

        int textLength = text.length();
        int blockAbsoluteStart = sqlBlock.getTextRange().getStartOffset();

        LinkedHashMap<TextRange, PsiElement> jsElements =
                collectJsElements(sqlBlock, blockAbsoluteStart);

        List<TextRange> jsRanges = new ArrayList<>(jsElements.keySet());
        jsRanges.sort(Comparator.comparingInt(TextRange::getStartOffset));

        if (jsRanges.isEmpty()) {
            registrar.startInjecting(BigQueryDialect.INSTANCE);
            registrar.addPlace(null, null, sqlBlock, new TextRange(0, textLength));
            registrar.doneInjecting();
            return;
        }

        for (TextRange range : jsRanges) {
            if (range.getStartOffset() < 0 || range.getEndOffset() > textLength) return;
        }

        if (hasOverlappingRanges(jsRanges)) return;

        VirtualFile vFile = sqlBlock.getContainingFile().getVirtualFile();
        String currentFileName = vFile != null ? vFile.getNameWithoutExtension() : null;

        registrar.startInjecting(BigQueryDialect.INSTANCE);

        int currentPos = 0;
        boolean hasAddedFragment = false;

        for (TextRange jsRange : jsRanges) {
            int fragmentStart = currentPos;
            int fragmentEnd = jsRange.getStartOffset();

            if (fragmentStart < fragmentEnd) {
                registrar.addPlace(
                        hasAddedFragment ? "" : null,
                        null,
                        sqlBlock,
                        new TextRange(fragmentStart, fragmentEnd)
                );
                hasAddedFragment = true;
            }

            PsiElement jsElement = jsElements.get(jsRange);
            String placeholder = (jsElement != null)
                    ? SqlxRefSelfResolver.resolveToSqlIdentifier(jsElement, currentFileName)
                    : null;
            if (placeholder == null) placeholder = "NULL";

            registrar.addPlace(
                    placeholder,
                    "",
                    sqlBlock,
                    new TextRange(jsRange.getStartOffset(), jsRange.getStartOffset())
            );

            currentPos = jsRange.getEndOffset();
        }

        if (currentPos < textLength) {
            registrar.addPlace(
                    hasAddedFragment ? "" : null,
                    null,
                    sqlBlock,
                    new TextRange(currentPos, textLength)
            );
            hasAddedFragment = true;
        }

        if (hasAddedFragment) {
            registrar.doneInjecting();
        }
    }

    @NotNull
    @Override
    public List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
        return List.of(SqlxSqlBlock.class);
    }
}