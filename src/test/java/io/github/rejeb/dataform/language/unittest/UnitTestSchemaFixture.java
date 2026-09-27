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
package io.github.rejeb.dataform.language.unittest;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.ServiceContainerUtil;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.lineage.column.ColumnRef;
import io.github.rejeb.dataform.language.schema.sql.DataformTableSchemaService;
import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;
import io.github.rejeb.dataform.language.schema.sql.model.DataformDasTable;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class UnitTestSchemaFixture {

    public static final List<ColumnInfo> ORDERS = List.of(
            new ColumnInfo("order_id", "STRING", "NULLABLE", "Order key"),
            new ColumnInfo("total", "NUMERIC", "NULLABLE", null),
            new ColumnInfo("address", "STRUCT", "NULLABLE", null, List.of(
                    new ColumnInfo("city", "STRING", "NULLABLE", null),
                    new ColumnInfo("geo", "STRUCT", "NULLABLE", null, List.of(
                            new ColumnInfo("lat", "FLOAT64", "NULLABLE", null))))),
            new ColumnInfo("items", "STRUCT", "REPEATED", null, List.of(
                    new ColumnInfo("sku", "STRING", "NULLABLE", null),
                    new ColumnInfo("qty", "INT64", "NULLABLE", null))));
    public static final List<ColumnInfo> RAW_ORDERS = List.of(
            new ColumnInfo("id", "STRING", "NULLABLE", null),
            new ColumnInfo("amount", "FLOAT64", "NULLABLE", null));
    public static final List<ColumnInfo> CUSTOMERS = List.of(
            new ColumnInfo("customer_id", "STRING", "NULLABLE", null),
            new ColumnInfo("name", "STRING", "NULLABLE", null));

    private UnitTestSchemaFixture() {
    }

    public static void install(Project project, Disposable disposable) {
        install(project, disposable, Map.of(
                "p.d.orders", ORDERS, "p.raw.raw_orders", RAW_ORDERS, "p.d.customers", CUSTOMERS));
    }

    public static void install(Project project, Disposable disposable, Map<String, List<ColumnInfo>> schemas) {
        Map<String, DataformDasTable> tables = new HashMap<>();
        schemas.forEach((fullName, columns) -> tables.put(fullName, new DataformDasTable(
                PsiManager.getInstance(project), fullName,
                fullName.substring(fullName.lastIndexOf('.') + 1), columns, null)));
        ServiceContainerUtil.replaceService(project, DataformTableSchemaService.class,
                new StubSchemaService(tables), disposable);
    }

    private record StubSchemaService(Map<String, DataformDasTable> tables) implements DataformTableSchemaService {

        @Override
        public void refreshAsync(@NotNull CompiledGraph graph, boolean forceRefresh) {
        }

        @Override
        public void refreshAsync(@NotNull CompiledGraph graph, boolean forceRefresh,
                                 @NotNull Set<String> failedFileNames) {
        }

        @Override
        public @NotNull Map<String, DataformDasTable> getAllTables() {
            return tables;
        }

        @Override
        public void renameColumn(@NotNull Set<ColumnRef> columns, @NotNull String newName,
                                 @NotNull Collection<VirtualFile> written) {
        }

        @Override
        public State getState() {
            return new State();
        }

        @Override
        public void loadState(@NotNull State state) {
        }

        @Override
        public long getModificationCount() {
            return 0;
        }
    }
}
