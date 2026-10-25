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
package io.github.rejeb.dataform.language.columns.rename.usage;

import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import io.github.rejeb.dataform.language.columns.model.ColumnRef;
import io.github.rejeb.dataform.language.columns.rename.DataformColumnNameValidator;
import io.github.rejeb.dataform.language.unittest.columns.TestColumnAlias;
import io.github.rejeb.dataform.language.unittest.columns.TestColumnAliases;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Collects the aliases of the unit tests standing for a column of the rename: the rows an input
 * mocks for an upstream table, and the rows the expected output lists for the tested dataset.
 */
public final class TestRenameEditCollector {

    private TestRenameEditCollector() {
    }

    /**
     * Returns the edits renaming, in the compiled unit test files, every alias standing for one of
     * the given columns.
     */
    @NotNull
    public static List<ColumnRenameEdit> collect(@NotNull Project project,
                                                 @NotNull Set<ColumnRef> columns,
                                                 @NotNull String newName) {
        List<ColumnRenameEdit> edits = new ArrayList<>();
        for (TestColumnAlias alias : TestColumnAliases.getInstance(project).of(columns)) {
            ContainerUtil.addIfNotNull(edits, EditFactory.ofWhole(alias.identifier(),
                    DataformColumnNameValidator.inSql(newName), ColumnRenameEdit.Kind.TEST_ALIAS,
                    ColumnRenameEdit.Risk.CERTAIN, "test alias of " + alias.column().columnName()));
        }
        return edits;
    }
}
