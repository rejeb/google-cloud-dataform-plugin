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
package io.github.rejeb.dataform.language.columns.usages;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.util.Processor;
import io.github.rejeb.dataform.language.schema.sql.model.StructColumnPath;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Where a column is read.
 *
 * <p>A column of a table and a field inside one are not found the same way. A column is a
 * declaration references point at, so the reference search finds it. A field is not: the platform
 * resolves a field to an element of its column's type rather than to anything of this plugin's, so
 * no reference ever names it and a search over references comes back empty however many files read
 * it. Each is given the search that can actually find it.</p>
 *
 * <p>A column of a table is also read where its name is handed to JavaScript as a string, which no
 * reference points at either; those reads follow the ones the reference search finds. So do the
 * actions selecting a star from its table, which read it without naming it.</p>
 */
interface ColumnUsageSearch {

    /**
     * The search that can find what a window was opened on.
     */
    static @NotNull ColumnUsageSearch of(@NotNull Project project,
                                         @NotNull ColumnWindowTarget target) {
        ColumnUsageSearch tests = new TestAliasColumnUsageSearch(project, target);
        StructColumnPath path = target.structPath();
        if (path != null) return chain(new StructFieldColumnUsageSearch(project, path), tests);
        ColumnUsageSearch references = (reads, maxReads) -> {
            for (PsiElement searched : target.searchTargets()) {
                if (!ColumnOccurrences.forEachReference(project, searched, reads)) return;
            }
        };
        ColumnUsageSearch stars = new StarColumnUsageSearch(project, target);
        ColumnUsageSearch strings = new JsStringColumnUsageSearch(project, target);
        return chain(chain(chain(references, stars), strings), tests);
    }

    /**
     * The reads of {@code first}, then those of {@code second} unless the caller stopped the first.
     */
    private static @NotNull ColumnUsageSearch chain(@NotNull ColumnUsageSearch first,
                                                    @NotNull ColumnUsageSearch second) {
        return (reads, maxReads) -> {
            AtomicBoolean stopped = new AtomicBoolean();
            first.forEachRead(read -> {
                if (reads.process(read)) return true;
                stopped.set(true);
                return false;
            }, maxReads);
            if (!stopped.get()) second.forEachRead(reads, maxReads);
        };
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
