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
import io.github.rejeb.dataform.language.lineage.column.ColumnRef;
import io.github.rejeb.dataform.language.schema.sql.ColumnOriginService;
import io.github.rejeb.dataform.language.schema.sql.model.DataformDasColumn;
import io.github.rejeb.dataform.language.schema.sql.model.StructColumnPath;
import io.github.rejeb.dataform.language.unittest.columns.TestColumnAlias;
import io.github.rejeb.dataform.language.unittest.columns.TestColumnAliases;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Finds the aliases of the unit tests standing for the column of a window: the rows an input mocks
 * for its table, and the rows the expected output lists for the tested dataset. No reference points
 * at them, since an alias names the column rather than reading it.
 */
final class TestAliasColumnUsageSearch implements ColumnUsageSearch {

    private final Project project;
    private final ColumnWindowTarget target;

    TestAliasColumnUsageSearch(@NotNull Project project, @NotNull ColumnWindowTarget target) {
        this.project = project;
        this.target = target;
    }

    @Override
    public void forEachRead(@NotNull Processor<PsiElement> reads, int maxReads) {
        Set<ColumnRef> columns = columnsOf(target);
        if (columns.isEmpty()) {
            return;
        }
        for (TestColumnAlias alias : TestColumnAliases.getInstance(project).of(columns)) {
            if (target.searchTargets().contains(alias.identifier())) {
                continue;
            }
            if (!reads.process(alias.identifier())) {
                return;
            }
        }
    }

    private @NotNull Set<ColumnRef> columnsOf(@NotNull ColumnWindowTarget target) {
        ColumnOriginService origins = ColumnOriginService.getInstance(project);
        Set<ColumnRef> columns = new LinkedHashSet<>();
        StructColumnPath path = target.structPath();
        if (path != null) {
            ColumnRef root = origins.reference(path.root());
            if (root != null) {
                columns.add(new ColumnRef(root.tableFullName(), path.dottedName()));
            }
            return columns;
        }
        for (PsiElement searched : target.searchTargets()) {
            if (searched instanceof DataformDasColumn column) {
                ColumnRef reference = origins.reference(column);
                if (reference != null) {
                    columns.add(reference);
                }
            }
        }
        return columns;
    }
}
