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

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.util.xmlb.annotations.Tag;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.lineage.column.ColumnRef;
import io.github.rejeb.dataform.language.schema.sql.model.DataformDasTable;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;
import java.util.Set;


public interface DataformTableSchemaService extends PersistentStateComponent<DataformTableSchemaService.State>, ModificationTracker {
    static DataformTableSchemaService getInstance(Project project) {
        return project.getService(DataformTableSchemaService.class);

    }

    void refreshAsync(@NotNull CompiledGraph graph, boolean forceRefresh);

    /**
     * Refreshes the schemas of the compiled graph, skipping the actions declared in the given
     * source files. Callers pass the files the last compilation reported as failing so that the
     * actions that did compile are still refreshed, while the failing ones keep their last known
     * schema instead of being re-extracted from stale or absent SQL.
     */
    void refreshAsync(@NotNull CompiledGraph graph,
                      boolean forceRefresh,
                      @NotNull java.util.Set<String> failedFileNames);

    @NotNull
    Map<String, DataformDasTable> getAllTables();

    /**
     * Renames a column in the schemas already published, so that the editor resolves it under its
     * new name at once instead of at the end of the next compilation.
     *
     * <p>These schemas are what a column reference resolves against, and they are read from a
     * compiled project. Compiling one takes long enough — the CLI copies the project and installs
     * its dependencies — that a column just renamed would be painted as unknown for the whole run,
     * everywhere it is read. A rename knows exactly which action publishes which column under which
     * name, so the same change is written here and the next extraction confirms it.</p>
     *
     * <p>What is written here is a guess, and it is kept only while the files the rename wrote go
     * on holding what it left in them. Undoing the rename or rolling it back puts the read answer
     * back at once, rather than leaving the editor resolving against a name no file carries.</p>
     *
     * @param columns the renamed columns, each named in the table publishing it under its old name
     * @param newName the name those columns now carry
     * @param written the files the rename wrote, which is what the guess is worth
     */
    void renameColumn(@NotNull Set<ColumnRef> columns,
                      @NotNull String newName,
                      @NotNull Collection<VirtualFile> written);


    class State {
        @Tag("schemaCacheJson")
        public String schemaCacheJson = null;
    }
}
