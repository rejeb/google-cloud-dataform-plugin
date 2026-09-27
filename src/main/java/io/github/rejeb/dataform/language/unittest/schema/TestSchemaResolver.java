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
package io.github.rejeb.dataform.language.unittest.schema;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import io.github.rejeb.dataform.language.compilation.model.Target;
import io.github.rejeb.dataform.language.psi.SqlxSqlBlock;
import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public interface TestSchemaResolver {

    /**
     * Returns the project instance of the resolver.
     */
    static TestSchemaResolver getInstance(@NotNull Project project) {
        return project.getService(TestSchemaResolver.class);
    }

    /**
     * Returns the SQL block of a unit test the given element belongs to: the block holding it, the
     * host block of an injected fragment, or the body of the input whose label holds it.
     */
    @NotNull
    Optional<SqlxSqlBlock> blockAt(@NotNull PsiElement element);

    /**
     * Returns the table a block of a unit test describes: the target named by the label of an
     * input, or the tested dataset for the expected output. Empty for any other block, outside a
     * unit test, or when the target or its schema is unknown. Reads injected PSI: call it under a
     * read action, off the EDT.
     */
    @NotNull
    Optional<TestBlockSchema> resolve(@NotNull SqlxSqlBlock block);

    /**
     * Resolves the block {@link #blockAt} finds for the given element.
     */
    @NotNull
    Optional<TestBlockSchema> resolveAt(@NotNull PsiElement element);

    /**
     * Returns the columns last extracted for a target, empty when none were.
     */
    @NotNull
    List<ColumnInfo> columnsOf(@Nullable Target target);
}
