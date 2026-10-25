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
import io.github.rejeb.dataform.language.schema.sql.model.DataformDasColumn;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The reads of a column no reference points at: its name handed to JavaScript as a string, and the
 * aliases of the unit tests standing for it. The column window finds them with the reference reads;
 * Find Usages, which runs the reference search on its own, asks for these separately.
 */
public final class UnreferencedColumnReads {

    private UnreferencedColumnReads() {
    }

    /**
     * Collects the reads of a column that no reference finds. Runs under the read action of its caller.
     *
     * @param project the project
     * @param column  the schema column
     * @return the string literals and test aliases reading the column
     */
    public static @NotNull List<PsiElement> of(@NotNull Project project, @NotNull DataformDasColumn column) {
        ColumnWindowTarget target = new ColumnWindowTarget(List.of(column), column.getName(), List.of(), List.of(), null);
        List<PsiElement> reads = Collections.synchronizedList(new ArrayList<>());
        Processor<PsiElement> collect = read -> {
            reads.add(read);
            return true;
        };
        new JsStringColumnUsageSearch(project, target).forEachRead(collect, Integer.MAX_VALUE);
        new TestAliasColumnUsageSearch(project, target).forEachRead(collect, Integer.MAX_VALUE);
        return reads;
    }
}
