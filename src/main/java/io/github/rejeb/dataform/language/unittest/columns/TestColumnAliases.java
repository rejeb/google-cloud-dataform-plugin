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
package io.github.rejeb.dataform.language.unittest.columns;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import io.github.rejeb.dataform.language.lineage.column.ColumnRef;
import io.github.rejeb.dataform.language.psi.SqlxSqlBlock;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface TestColumnAliases {

    /**
     * Returns the project instance of the service.
     */
    static TestColumnAliases getInstance(@NotNull Project project) {
        return project.getService(TestColumnAliases.class);
    }

    /**
     * Returns the alias of a unit test holding the given element, host or injected, with the
     * column it stands for.
     */
    @NotNull
    Optional<TestColumnAlias> at(@NotNull PsiElement position);

    /**
     * Returns every alias of an input or of the expected output of a unit test that is written in
     * the file, with the column of the block's table it stands for.
     */
    @NotNull
    List<TestColumnAlias> in(@NotNull SqlxSqlBlock block);

    /**
     * Returns the aliases of the compiled unit tests standing for one of the given columns. Reads
     * injected PSI: call it under a read action, off the EDT.
     */
    @NotNull
    List<TestColumnAlias> of(@NotNull Set<ColumnRef> columns);
}
