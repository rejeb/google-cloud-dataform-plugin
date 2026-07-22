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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PreOperationsFilterTest {

    @Test
    public void keepsSideEffectFreeStatements() {
        assertTrue(PreOperationsFilter.isReadOnly("DECLARE max_date DATE"));
        assertTrue(PreOperationsFilter.isReadOnly("SET max_date = (SELECT MAX(d) FROM t)"));
        assertTrue(PreOperationsFilter.isReadOnly("select 1"));
        assertTrue(PreOperationsFilter.isReadOnly("WITH a AS (SELECT 1) SELECT * FROM a"));
        assertTrue(PreOperationsFilter.isReadOnly(
                "CREATE TEMP FUNCTION f(x INT64) AS (x + 1)"));
        assertTrue(PreOperationsFilter.isReadOnly(
                "CREATE OR REPLACE TEMPORARY FUNCTION f(x INT64) AS (x)"));
    }

    @Test
    public void dropsMutatingStatements() {
        assertFalse(PreOperationsFilter.isReadOnly("INSERT INTO t VALUES (1)"));
        assertFalse(PreOperationsFilter.isReadOnly("UPDATE t SET a = 1 WHERE b = 2"));
        assertFalse(PreOperationsFilter.isReadOnly("DELETE FROM t WHERE a = 1"));
        assertFalse(PreOperationsFilter.isReadOnly("MERGE t USING s ON t.id = s.id"));
        assertFalse(PreOperationsFilter.isReadOnly("TRUNCATE TABLE t"));
        assertFalse(PreOperationsFilter.isReadOnly("DROP TABLE t"));
        assertFalse(PreOperationsFilter.isReadOnly("ALTER TABLE t ADD COLUMN c INT64"));
        assertFalse(PreOperationsFilter.isReadOnly("CREATE SCHEMA IF NOT EXISTS ds"));
        assertFalse(PreOperationsFilter.isReadOnly(
                "CREATE OR REPLACE TABLE t AS SELECT 1"));
        assertFalse(PreOperationsFilter.isReadOnly("CALL my_proc()"));
        assertFalse(PreOperationsFilter.isReadOnly("BEGIN INSERT INTO t VALUES (1); END"));
    }

    @Test
    public void dropsBlankAndNullStatements() {
        assertFalse(PreOperationsFilter.isReadOnly(null));
        assertFalse(PreOperationsFilter.isReadOnly("   "));
        assertFalse(PreOperationsFilter.isReadOnly("-- only a comment"));
    }

    @Test
    public void ignoresLeadingComments() {
        assertTrue(PreOperationsFilter.isReadOnly("-- set the cutoff\nDECLARE d DATE"));
        assertTrue(PreOperationsFilter.isReadOnly("/* block */ SET d = CURRENT_DATE()"));
        assertFalse(PreOperationsFilter.isReadOnly("-- harmless looking\nDELETE FROM t"));
    }

    @Test
    public void splitsMultiStatementEntriesAndKeepsOnlySafeOnes() {
        List<String> kept = PreOperationsFilter.keepReadOnly(List.of(
                "DECLARE d DATE;\nDELETE FROM t WHERE x = 1;\nSET d = CURRENT_DATE();"));
        assertEquals(2, kept.size());
        assertEquals("DECLARE d DATE", kept.get(0));
        assertEquals("SET d = CURRENT_DATE()", kept.get(1));
    }

    @Test
    public void doesNotSplitOnSemicolonInsideStringLiteral() {
        List<String> kept = PreOperationsFilter.keepReadOnly(List.of(
                "SET s = 'a;b'"));
        assertEquals(List.of("SET s = 'a;b'"), kept);
    }

    @Test
    public void returnsEmptyListForNullOrEmptyInput() {
        assertEquals(List.of(), PreOperationsFilter.keepReadOnly(null));
        assertEquals(List.of(), PreOperationsFilter.keepReadOnly(List.of()));
    }
}
