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

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LightVirtualFile;
import io.github.rejeb.dataform.language.compilation.DataformCompilationService;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.compilation.model.Target;
import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;
import io.github.rejeb.dataform.language.schema.sql.model.DataformDasTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves the output columns of the action a SQLX file declares, by matching the file against the
 * compiled graph and reading the schema the last extraction published for its target.
 */
public final class DataformActionColumns {

    private DataformActionColumns() {
    }

    /**
     * Returns the table declared by the file the given element belongs to, resolving injected
     * fragments to their SQLX host, empty when the file declares none or when its schema has not
     * been extracted yet.
     */
    @NotNull
    public static Optional<DataformDasTable> tableAt(@NotNull PsiElement element) {
        return tableIn(element.getContainingFile());
    }

    /**
     * Returns the table declared by the given file, resolving injected fragments to their SQLX
     * host, empty when the file declares none or when its schema has not been extracted yet.
     */
    @NotNull
    private static Optional<DataformDasTable> tableIn(@Nullable PsiFile file) {
        if (file == null) {
            return Optional.empty();
        }
        Project project = file.getProject();
        PsiFile hostFile = InjectedLanguageManager.getInstance(project).getTopLevelFile(file);
        return hostFile == null
                ? Optional.empty()
                : tableOf(project, sourceOf(hostFile.getVirtualFile()));
    }

    /**
     * The file an action was compiled from. A file the platform copied to work on, as completion
     * does, carries no path of its own and has to be resolved back to the file it was copied from.
     */
    @Nullable
    private static VirtualFile sourceOf(@Nullable VirtualFile file) {
        return file instanceof LightVirtualFile light && light.getOriginalFile() != null
                ? light.getOriginalFile()
                : file;
    }

    @NotNull
    private static Optional<DataformDasTable> tableOf(@NotNull Project project,
                                                      @Nullable VirtualFile file) {
        if (file == null) {
            return Optional.empty();
        }
        CompiledGraph graph = DataformCompilationService.getInstance(project).getCompiledGraph();
        if (graph == null) {
            return Optional.empty();
        }
        Map<String, DataformDasTable> tables =
                DataformTableSchemaService.getInstance(project).getAllTables();
        for (String fullName : targetsOf(graph, file.getPath())) {
            DataformDasTable table = tables.get(fullName);
            if (table != null && !table.getColumns().isEmpty()) {
                return Optional.of(table);
            }
        }
        return Optional.empty();
    }

    /**
     * Returns the columns of the table the given element's file declares, empty when there are none
     * to offer.
     */
    @NotNull
    public static List<ColumnInfo> at(@NotNull PsiElement element) {
        return tableAt(element).map(DataformDasTable::getColumns).orElseGet(List::of);
    }

    /**
     * Returns the columns of the table the given file declares, empty when there are none to offer.
     */
    @NotNull
    public static List<ColumnInfo> in(@Nullable PsiFile file) {
        return tableIn(file).map(DataformDasTable::getColumns).orElseGet(List::of);
    }

    /**
     * Walks the given record columns down the path, returning the columns reachable at its end. An
     * empty path returns the columns unchanged.
     */
    @NotNull
    public static List<ColumnInfo> descend(@NotNull List<ColumnInfo> columns,
                                           @NotNull List<String> recordPath) {
        List<ColumnInfo> current = columns;
        for (String name : recordPath) {
            Optional<ColumnInfo> column = find(current, name);
            if (column.isEmpty()) {
                return List.of();
            }
            current = column.get().subFields();
        }
        return current;
    }

    /**
     * Finds a column by name, ignoring case as BigQuery column names are case insensitive.
     */
    @NotNull
    public static Optional<ColumnInfo> find(@NotNull List<ColumnInfo> columns, @NotNull String name) {
        return columns.stream().filter(column -> column.name().equalsIgnoreCase(name)).findFirst();
    }

    @NotNull
    private static List<String> targetsOf(@NotNull CompiledGraph graph, @NotNull String path) {
        List<String> fullNames = new ArrayList<>();
        graph.findTableByFileName(path).forEach(table -> add(fullNames, table.getTarget()));
        graph.findOperationByFileName(path).forEach(operation -> add(fullNames, operation.getTarget()));
        graph.findDeclarationByFileName(path).forEach(declaration -> add(fullNames, declaration.getTarget()));
        return fullNames;
    }

    private static void add(@NotNull List<String> fullNames, @Nullable Target target) {
        if (target != null && target.getFullName() != null) {
            fullNames.add(target.getFullName());
        }
    }
}
