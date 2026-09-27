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
package io.github.rejeb.dataform.language.injection;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A template hole may stand for a whole common table expression an include writes. Where it does,
 * the filler must be one as well, or the query around it no longer parses.
 */
public class SqlCteSlotTest {

    @Test
    public void aHoleRightAfterWithOpensACte() {
        assertTrue(SqlCteSlot.opensACte("WITH "));
        assertTrue(SqlCteSlot.opensACte("-- a comment\n\nwith\n    "));
        assertTrue(SqlCteSlot.opensACte("WITH RECURSIVE "));
    }

    @Test
    public void aHoleAfterAnotherCteOpensACte() {
        assertTrue(SqlCteSlot.opensACte("WITH a AS (SELECT 1, 2 FROM t), "));
        assertTrue(SqlCteSlot.opensACte("WITH ${first()}, "));
    }

    @Test
    public void aHoleInASelectListIsAnExpression() {
        assertFalse(SqlCteSlot.opensACte("SELECT a, "));
        assertFalse(SqlCteSlot.opensACte("WITH a AS (SELECT 1) SELECT x, "));
    }

    @Test
    public void aHoleElsewhereIsAnExpression() {
        assertFalse(SqlCteSlot.opensACte(""));
        assertFalse(SqlCteSlot.opensACte("SELECT a FROM t WHERE "));
        assertFalse(SqlCteSlot.opensACte("SELECT a FROM t WHERE b IN (1, "));
        assertFalse(SqlCteSlot.opensACte("SELECT a AS width, "));
    }
}
