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
package io.github.rejeb.dataform.language.schema.sql;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.usages.impl.rules.UsageType;
import com.intellij.usages.impl.rules.UsageTypeProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Sorts the rows of a Dataform column search into the place the column is declared and the places
 * that read it, so the usage view groups them under headings instead of listing one flat run of
 * identical names.
 */
public class DataformColumnUsageTypeProvider implements UsageTypeProvider {

    static final UsageType DECLARATION = new UsageType(() -> "Column declaration");
    static final UsageType READ = new UsageType(() -> "Column read");

    @Override
    public @Nullable UsageType getUsageType(@NotNull PsiElement element) {
        PsiFile containing = element.getContainingFile();
        if (containing == null) return null;
        PsiFile topLevel = InjectedLanguageManager.getInstance(element.getProject())
                .getTopLevelFile(containing);
        if (topLevel == null || !topLevel.getName().endsWith(".sqlx")) return null;

        ColumnOriginService origins = ColumnOriginService.getInstance(element.getProject());
        return origins.declaredColumn(topLevel, element) != null ? DECLARATION : READ;
    }
}
