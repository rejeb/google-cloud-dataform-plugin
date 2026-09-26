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
package io.github.rejeb.dataform.language.util;

import com.intellij.openapi.vfs.VirtualFile;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class UtilsTest {

    @Test
    public void withPreOperationsReturnsTheQueryUnchangedWhenThereAreNone() {
        assertEquals("SELECT 1", Utils.withPreOperations(null, "SELECT 1"));
        assertEquals("SELECT 1", Utils.withPreOperations(List.of(), "SELECT 1"));
    }

    @Test
    public void withPreOperationsTerminatesEachStatementAndTheQuery() {
        String script = Utils.withPreOperations(
                List.of("DECLARE d DATE", "SET d = CURRENT_DATE();"), "SELECT d");
        assertEquals("DECLARE d DATE;\nSET d = CURRENT_DATE();\nSELECT d;", script);
    }

    @Test
    public void withPreOperationsDropsBlankAndNullStatements() {
        String script = Utils.withPreOperations(
                Arrays.asList(null, "  ", " DECLARE x INT64 "), "SELECT x");
        assertEquals("DECLARE x INT64;\nSELECT x;", script);
    }

    @Test
    public void formatBytesUsesTheRightUnit() {
        assertEquals("—", Utils.formatBytes(null));
        assertEquals("—", Utils.formatBytes(-1L));
        assertEquals("512 B", Utils.formatBytes(512L));
        assertEquals(String.format("%.1f KB", 1.0), Utils.formatBytes(1_024L));
        assertEquals(String.format("%.1f MB", 1.5), Utils.formatBytes(1_572_864L));
        assertEquals(String.format("%.2f GB", 2.0), Utils.formatBytes(2L * 1_073_741_824L));
    }

    @Test
    public void actionFilesLiveUnderDefinitionsAndAreWritable() {
        assertTrue(Utils.isActionFile(file("/p/definitions/a.sqlx", true)));
        assertTrue(Utils.isActionFile(file("C:\\p\\definitions\\sub\\a.js", true)));
        assertFalse(Utils.isActionFile(file("/p/definitions/a.sqlx", false)));
        assertFalse(Utils.isActionFile(file("/p/includes/a.js", true)));
        assertFalse(Utils.isActionFile(file("/p/definitions/notes.md", true)));
    }

    private static VirtualFile file(String path, boolean writable) {
        VirtualFile file = mock(VirtualFile.class);
        when(file.getPath()).thenReturn(path);
        when(file.isWritable()).thenReturn(writable);
        return file;
    }
}
