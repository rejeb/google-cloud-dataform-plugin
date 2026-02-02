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
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.sql.dialects.bigquery.BigQueryDialect;
import io.github.rejeb.dataform.language.psi.SqlxSqlBlock;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static io.github.rejeb.dataform.language.injection.InjectionHelper.collectJsElementRanges;
import static io.github.rejeb.dataform.language.injection.InjectionHelper.hasOverlappingRanges;

public class SqlxSqlInjector implements MultiHostInjector {
    @Override
    public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar,
                                     @NotNull PsiElement context) {

        if (!(context instanceof SqlxSqlBlock sqlBlock)) return;

        String text = sqlBlock.getText();
        if (text == null || text.isEmpty()) return;

        if (PsiTreeUtil.hasErrorElements(sqlBlock)) {
            return;
        }

        int textLength = text.length();

        List<TextRange> jsRanges = collectJsElementRanges(sqlBlock);

        if (jsRanges.isEmpty()) {
            registrar.startInjecting(BigQueryDialect.INSTANCE);
            registrar.addPlace(null, null, sqlBlock, new TextRange(0, textLength));
            registrar.doneInjecting();
            return;
        }

        jsRanges.sort(Comparator.comparingInt(TextRange::getStartOffset));

        for (TextRange range : jsRanges) {
            if (range.getStartOffset() < 0 || range.getEndOffset() > textLength) {
                return;
            }
        }

        if (hasOverlappingRanges(jsRanges)) {
            return;
        }

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

            registrar.addPlace(
                    "NULL",
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
        return Collections.singletonList(SqlxSqlBlock.class);
    }

}