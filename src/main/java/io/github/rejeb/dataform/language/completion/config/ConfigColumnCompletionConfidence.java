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
package io.github.rejeb.dataform.language.completion.config;

import com.intellij.codeInsight.completion.CompletionConfidence;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;

/**
 * Opens the completion popup as the user types a column name in a SQLX config block.
 *
 * <p>JavaScript skips the automatic popup inside a string literal, which leaves the column names
 * of {@code clusterBy}, {@code partitionBy} or the {@code assertions} block reachable through an
 * explicit completion request only, while the keys of the {@code columns} map, being identifiers,
 * pop up on their own.</p>
 */
public class ConfigColumnCompletionConfidence extends CompletionConfidence {

    @Override
    public @NotNull ThreeState shouldSkipAutopopup(@NotNull PsiElement contextElement,
                                                   @NotNull PsiFile psiFile,
                                                   int offset) {
        return ConfigColumnSlots.at(contextElement).isPresent() ? ThreeState.NO : ThreeState.UNSURE;
    }
}
