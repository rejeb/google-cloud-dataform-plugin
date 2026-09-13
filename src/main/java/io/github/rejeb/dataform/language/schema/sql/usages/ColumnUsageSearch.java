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
package io.github.rejeb.dataform.language.schema.sql.usages;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.util.Processor;
import io.github.rejeb.dataform.language.schema.sql.model.StructColumnPath;
import org.jetbrains.annotations.NotNull;

/**
 * Where a column is read.
 *
 * <p>A column of a table and a field inside one are not found the same way. A column is a
 * declaration references point at, so the reference search finds it. A field is not: the platform
 * resolves a field to an element of its column's type rather than to anything of this plugin's, so
 * no reference ever names it and a search over references comes back empty however many files read
 * it. Each is given the search that can actually find it.</p>
 */
interface ColumnUsageSearch {

    /**
     * The search that can find what a window was opened on.
     */
    static @NotNull ColumnUsageSearch of(@NotNull Project project,
                                         @NotNull ColumnWindowTarget target) {
        StructColumnPath path = target.structPath();
        return path == null
                ? new ReferenceColumnUsageSearch(project, target)
                : new StructFieldColumnUsageSearch(project, path);
    }

    /**
     * Feeds every place reading the column to {@code reads}, stopping as soon as it answers
     * {@code false}. Runs under the read action of its caller and may report from several threads.
     *
     * @param maxReads the reads the caller will keep, which a search paying for candidates it may
     *                 throw away scales its own effort by
     */
    void forEachRead(@NotNull Processor<PsiElement> reads, int maxReads);
}
