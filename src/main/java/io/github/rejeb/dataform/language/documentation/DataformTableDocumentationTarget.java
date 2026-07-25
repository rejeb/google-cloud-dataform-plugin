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
package io.github.rejeb.dataform.language.documentation;

import com.intellij.icons.AllIcons;
import com.intellij.model.Pointer;
import com.intellij.openapi.project.Project;
import com.intellij.platform.backend.documentation.DocumentationResult;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.presentation.TargetPresentation;
import io.github.rejeb.dataform.language.compilation.DataformCompilationService;
import io.github.rejeb.dataform.language.compilation.model.CompiledAssertion;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.compilation.model.CompiledOperation;
import io.github.rejeb.dataform.language.compilation.model.CompiledTable;
import io.github.rejeb.dataform.language.compilation.model.Declaration;
import io.github.rejeb.dataform.language.compilation.model.Target;
import io.github.rejeb.dataform.language.schema.sql.DataformTableSchemaService;
import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;
import io.github.rejeb.dataform.language.schema.sql.model.DataformDasTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class DataformTableDocumentationTarget implements DocumentationTarget {

    private final Project myProject;
    private final String myTableName;

    public DataformTableDocumentationTarget(@NotNull Project project, @NotNull String tableName) {
        this.myProject = project;
        this.myTableName = tableName;
    }

    @Override
    public @NotNull Pointer<? extends DocumentationTarget> createPointer() {
        return Pointer.hardPointer(this);
    }

    @Override
    public @NotNull TargetPresentation computePresentation() {
        return TargetPresentation.builder(myTableName)
                .icon(AllIcons.Nodes.DataTables)
                .presentation();
    }

    @Override
    public @Nullable DocumentationResult computeDocumentation() {
        CompiledGraph graph = myProject.getService(DataformCompilationService.class).getCompiledGraph();

        String fullName = null;
        String type = null;
        String sourceFile = null;
        String description = null;

        if (graph != null) {
            Optional<CompiledTable> table = graph.findTableByName(myTableName);
            if (table.isPresent()) {
                CompiledTable value = table.get();
                fullName = fullName(value.getTarget());
                type = value.getType();
                sourceFile = value.getFileName();
                description = value.getActionDescriptor() == null
                        ? null
                        : value.getActionDescriptor().getDescription();
            } else {
                Optional<Declaration> declaration = graph.findDeclarationByName(myTableName);
                if (declaration.isPresent()) {
                    fullName = fullName(declaration.get().getTarget());
                    type = "declaration";
                    sourceFile = declaration.get().getFileName();
                } else {
                    Optional<CompiledAssertion> assertion = graph.findAssertionByName(myTableName);
                    if (assertion.isPresent()) {
                        fullName = fullName(assertion.get().getTarget());
                        type = "assertion";
                        sourceFile = assertion.get().getFileName();
                    } else {
                        Optional<CompiledOperation> operation = graph.findOperationByName(myTableName);
                        if (operation.isPresent()) {
                            fullName = fullName(operation.get().getTarget());
                            type = "operation";
                            sourceFile = operation.get().getFileName();
                        }
                    }
                }
            }
        }

        String html = DataformDocumentationRenderer.renderTable(
                myTableName, fullName, type, sourceFile, description, columns());
        return DocumentationResult.documentation(html);
    }

    private List<ColumnInfo> columns() {
        Map<String, DataformDasTable> tables =
                DataformTableSchemaService.getInstance(myProject).getAllTables();
        for (DataformDasTable table : tables.values()) {
            if (table.getName().equalsIgnoreCase(myTableName)) {
                return table.getColumns();
            }
        }
        return List.of();
    }

    @Nullable
    private static String fullName(@Nullable Target target) {
        return target == null ? null : target.getFullName();
    }
}
