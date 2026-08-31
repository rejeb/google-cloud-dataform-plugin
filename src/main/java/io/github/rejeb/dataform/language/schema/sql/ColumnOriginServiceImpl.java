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

import com.intellij.database.model.DasTable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import io.github.rejeb.dataform.language.compilation.DataformCompilationService;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.compilation.model.CompiledTable;
import io.github.rejeb.dataform.language.lineage.column.ColumnLineageGraph;
import io.github.rejeb.dataform.language.lineage.column.ColumnRef;
import io.github.rejeb.dataform.language.lineage.service.LineageGraphService;
import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;
import io.github.rejeb.dataform.language.schema.sql.model.DataformDasColumn;
import io.github.rejeb.dataform.language.schema.sql.model.DataformDasTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class ColumnOriginServiceImpl implements ColumnOriginService {

    private final Project project;

    public ColumnOriginServiceImpl(@NotNull Project project) {
        this.project = project;
    }

    @Override
    public @Nullable ColumnRef declaredColumn(@NotNull PsiFile file, @NotNull PsiElement element) {
        String name = SqlxOutputColumnLocator.declaredColumnName(file, element);
        if (name == null) return null;
        String fullName = fullNameOf(file);
        return fullName == null ? null : new ColumnRef(fullName, name);
    }

    @Override
    public @Nullable PsiElement declaringElement(@NotNull ColumnRef column) {
        PsiFile file = sourceFileOf(column.tableFullName());
        if (file == null) return null;
        return SqlxOutputColumnLocator.findOutputColumn(file, column.columnName());
    }

    @Override
    public @Nullable PsiElement declaringElement(@NotNull DataformDasColumn column) {
        ColumnRef reference = reference(column);
        return reference == null ? null : declaringElement(reference);
    }

    /**
     * The column reference of a schema column, found by the table it belongs to.
     *
     * <p>The schema is keyed by full name, and two datasets of a project may well hold a table of
     * the same short name. A table that is not the very instance the schema holds is matched on the
     * logical identity {@link DataformDasTable#isEquivalentTo}, which is the name <em>and</em> the
     * file of the action building it, never on the short name alone.</p>
     */
    @Override
    public @Nullable ColumnRef reference(@NotNull DataformDasColumn column) {
        DasTable table = column.getTable();
        if (!(table instanceof DataformDasTable dataformTable)) return null;
        for (Map.Entry<String, DataformDasTable> entry : tables().entrySet()) {
            if (entry.getValue() == dataformTable || entry.getValue().isEquivalentTo(dataformTable)) {
                return new ColumnRef(entry.getKey(), column.getName());
            }
        }
        return null;
    }

    @Override
    public @NotNull Set<ColumnRef> origins(@NotNull ColumnRef column) {
        ColumnLineageGraph graph = LineageGraphService.getInstance(project).columnGraph();
        if (graph == null || graph.column(column.id()) == null) return Set.of();
        Set<ColumnRef> result = new LinkedHashSet<>();
        for (String id : graph.predecessors(column.id())) {
            ColumnRef ref = graph.column(id);
            if (ref != null) result.add(ref);
        }
        return result;
    }

    /**
     * The schema column, carrying the SQLX file of the action that builds it so that navigation
     * reaches the select-list item declaring it. The file is resolved through the compiled graph
     * rather than through {@code DataformDasTable#getContainingFile}, which falls back to a
     * throwaway document whenever the source file cannot be located on the local file system.
     */
    @Override
    public @Nullable DataformDasColumn dasColumn(@NotNull ColumnRef column) {
        DataformDasTable table = tables().get(column.tableFullName());
        if (table == null) return null;
        for (ColumnInfo info : table.getColumns()) {
            if (info.name().equalsIgnoreCase(column.columnName())) {
                PsiFile source = sourceFileOf(column.tableFullName());
                return new DataformDasColumn(PsiManager.getInstance(project), table, info,
                        source != null ? source : table.getContainingFile());
            }
        }
        return null;
    }

    private @NotNull Map<String, DataformDasTable> tables() {
        return DataformTableSchemaService.getInstance(project).getAllTables();
    }

    /**
     * The action a SQLX file builds, matched through the compiled graph rather than through the
     * file system: the graph records the project-relative file name of every action, and matching
     * on it holds wherever the project is opened from.
     */
    private @Nullable String fullNameOf(@NotNull PsiFile file) {
        String relativePath = relativePathOf(file);
        if (relativePath == null) return null;
        CompiledGraph graph = DataformCompilationService.getInstance(project).getCompiledGraph();
        if (graph == null) return null;
        for (CompiledTable table : graph.getTables()) {
            if (relativePath.equals(table.getFileName()) && table.getTarget() != null) {
                return table.getTarget().getFullName();
            }
        }
        return null;
    }

    private @Nullable PsiFile sourceFileOf(@NotNull String tableFullName) {
        CompiledGraph graph = DataformCompilationService.getInstance(project).getCompiledGraph();
        if (graph == null) return null;
        VirtualFile projectDir = ProjectUtil.guessProjectDir(project);
        if (projectDir == null) return null;
        for (CompiledTable table : graph.getTables()) {
            if (table.getTarget() == null
                    || !tableFullName.equals(table.getTarget().getFullName())
                    || table.getFileName() == null) {
                continue;
            }
            VirtualFile source = projectDir.findFileByRelativePath(table.getFileName());
            return source == null ? null : PsiManager.getInstance(project).findFile(source);
        }
        return null;
    }

    private @Nullable String relativePathOf(@NotNull PsiFile file) {
        VirtualFile virtualFile = file.getVirtualFile();
        if (virtualFile == null) return null;
        VirtualFile projectDir = ProjectUtil.guessProjectDir(project);
        if (projectDir == null) return null;
        return VfsUtilCore.getRelativePath(virtualFile, projectDir);
    }
}
