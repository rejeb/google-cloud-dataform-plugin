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
import io.github.rejeb.dataform.language.compilation.model.ActionReference;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.compilation.model.Target;
import io.github.rejeb.dataform.language.schema.sql.DataformTableSchemaService;
import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;
import io.github.rejeb.dataform.language.schema.sql.model.DataformDasTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class DataformTableDocumentationTarget implements DocumentationTarget {

    private final Project myProject;
    private final ActionReference myAction;
    private final String myTableName;

    public DataformTableDocumentationTarget(@NotNull Project project, @NotNull String tableName) {
        this(project, ActionReference.named(tableName));
    }

    public DataformTableDocumentationTarget(@NotNull Project project, @NotNull ActionReference action) {
        this.myProject = project;
        this.myAction = action;
        this.myTableName = action.name();
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
        return DocumentationResult.documentation(html());
    }

    String html() {
        CompiledGraph graph = myProject.getService(DataformCompilationService.class).getCompiledGraph();
        ActionInfo info = graph == null ? ActionInfo.NONE : graph.findTableByReference(myAction)
                .map(t -> new ActionInfo(fullName(t.getTarget()), t.getType(), t.getFileName(),
                        t.getActionDescriptor() == null ? null : t.getActionDescriptor().getDescription()))
                .or(() -> graph.findDeclarationByReference(myAction)
                        .map(d -> new ActionInfo(fullName(d.getTarget()), "declaration", d.getFileName(), null)))
                .or(() -> graph.findAssertionByReference(myAction)
                        .map(a -> new ActionInfo(fullName(a.getTarget()), "assertion", a.getFileName(), null)))
                .or(() -> graph.findOperationByReference(myAction)
                        .map(o -> new ActionInfo(fullName(o.getTarget()), "operation", o.getFileName(), null)))
                .orElse(ActionInfo.NONE);
        return DataformDocumentationRenderer.renderTable(myTableName, info.fullName(), info.type(),
                info.sourceFile(), info.description(), columns(info.fullName()));
    }

    private record ActionInfo(@Nullable String fullName, @Nullable String type, @Nullable String sourceFile,
                              @Nullable String description) {
        static final ActionInfo NONE = new ActionInfo(null, null, null, null);
    }

    private List<ColumnInfo> columns(@Nullable String fullName) {
        Map<String, DataformDasTable> tables =
                DataformTableSchemaService.getInstance(myProject).getAllTables();
        DataformDasTable resolved = fullName == null ? null : tables.get(fullName);
        if (resolved != null) {
            return resolved.getColumns();
        }
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
