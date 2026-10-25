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
package io.github.rejeb.dataform.language.fileEditor;

import io.github.rejeb.dataform.language.unittest.preview.TestQueries;
import com.google.gson.Gson;
import io.github.rejeb.dataform.language.compilation.model.CompiledTest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlxPreviewModeTest {

    @Test
    void actionFilesShowQuerySchemaAndLineage() {
        assertTrue(SqlxPreviewMode.ACTION.shows(SqlxCompiledPreviewEditor.View.QUERY));
        assertTrue(SqlxPreviewMode.ACTION.shows(SqlxCompiledPreviewEditor.View.SCHEMA));
        assertTrue(SqlxPreviewMode.ACTION.shows(SqlxCompiledPreviewEditor.View.LINEAGE));
        assertFalse(SqlxPreviewMode.ACTION.shows(SqlxCompiledPreviewEditor.View.TEST));
        assertEquals(SqlxCompiledPreviewEditor.View.LINEAGE, SqlxPreviewMode.ACTION.defaultView());
    }

    @Test
    void testFilesShowOnlyTheTestView() {
        for (SqlxCompiledPreviewEditor.View view : SqlxCompiledPreviewEditor.View.values()) {
            assertEquals(view == SqlxCompiledPreviewEditor.View.TEST, SqlxPreviewMode.UNIT_TEST.shows(view));
        }
        assertEquals(SqlxCompiledPreviewEditor.View.TEST, SqlxPreviewMode.UNIT_TEST.defaultView());
    }

    @Test
    void aTestOffersItsTwoQueriesToExecute() {
        CompiledTest test = new Gson().fromJson(
                "{\"name\": \"orders_test\", \"testQuery\": \"SELECT 1\", \"expectedOutputQuery\": \"SELECT 2\"}",
                CompiledTest.class);

        assertEquals(List.of(
                new TestQueries.Query("orders_test (test query)", "SELECT 1"),
                new TestQueries.Query("orders_test (expected output)", "SELECT 2")), TestQueries.of(test));
        assertEquals(List.of(), TestQueries.of(null));
    }
}
