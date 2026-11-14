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

import com.intellij.openapi.util.io.NioFiles;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.schema.sql.model.DataformDasTable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Restoring the persisted schemas never looks a source file up. The state is loaded on whatever
 * thread first asks for the service, the EDT included, where a file system lookup per table
 * freezes the IDE: the source of a table is looked up only when first asked for.
 */
public class SchemaCacheLoadTest extends BasePlatformTestCase {

    private static final String DIRECTORY = "schema-cache-load";

    private static DataformTableSchemaService.State state(String fileName) {
        DataformTableSchemaService.State state = new DataformTableSchemaService.State();
        state.schemaCacheJson = "{\"p.d.orders\":{\"columns\":[{\"name\":\"id\",\"type\":\"STRING\","
                + "\"mode\":\"NULLABLE\",\"subFields\":[]}],\"lastModified\":1700,\"fileName\":\""
                + fileName + "\"}}";
        return state;
    }

    public void testASourceFileCreatedAfterTheStateWasLoadedIsFound() throws Exception {
        String basePath = getProject().getBasePath();
        assertNotNull(basePath);
        Path directory = Path.of(basePath, DIRECTORY);
        SchemaCacheStore cache = new SchemaCacheStore(getProject());
        cache.load(state(DIRECTORY + "/orders.sqlx"));
        try {
            Path file = directory.resolve("orders.sqlx");
            Files.createDirectories(directory);
            Files.writeString(file, "SELECT 1 AS id\n");
            VirtualFile created = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file);
            assertNotNull(created);

            DataformDasTable table = cache.published().get("p.d.orders");

            assertEquals(created, table.getSourceFile());
        } finally {
            NioFiles.deleteRecursively(directory);
            LocalFileSystem.getInstance().refreshNioFiles(List.of(directory));
        }
    }
}
