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
package io.github.rejeb.dataform.language.completion;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.patterns.PlatformPatterns;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class DataformJsSymbolCompletionContributor extends CompletionContributor {

    public DataformJsSymbolCompletionContributor() {
        extend(
                CompletionType.BASIC,
                PlatformPatterns.psiElement(),
                new DataformJsSymbolCompletionContributorProvider()
        );
    }

    @Override
    public void fillCompletionVariants(@NotNull CompletionParameters parameters,
                                       @NotNull CompletionResultSet result) {
        super.fillCompletionVariants(parameters, result);

        if (result.isStopped() || !DataformJsSymbolCompletionContributorProvider.appliesTo(parameters)) {
            return;
        }
        Set<String> shadowed = DataformJsSymbolCompletionContributorProvider
                .shadowedIncludeNames(parameters.getPosition().getProject());
        if (shadowed.isEmpty()) {
            return;
        }
        result.runRemainingContributors(parameters, completionResult -> {
            if (!shadowed.contains(completionResult.getLookupElement().getLookupString())) {
                result.passResult(completionResult);
            }
        });
    }
}
