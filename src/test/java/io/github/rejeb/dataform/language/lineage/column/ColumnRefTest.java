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
package io.github.rejeb.dataform.language.lineage.column;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ColumnRefTest {

    @Test
    void idCombinesTableAndColumn() {
        ColumnRef ref = new ColumnRef("p.d.t", "amount");
        assertEquals("p.d.t#amount", ref.id());
    }

    @Test
    void tableNodeIdMatchesLineageNode() {
        ColumnRef ref = new ColumnRef("p.d.t", "amount");
        assertEquals("node:p.d.t", ref.tableNodeId());
    }

    @Test
    void equalityIsCaseInsensitiveOnColumnName() {
        assertEquals(new ColumnRef("p.d.t", "Amount"), new ColumnRef("p.d.t", "amount"));
        assertEquals(new ColumnRef("p.d.t", "Amount").hashCode(),
                     new ColumnRef("p.d.t", "amount").hashCode());
    }

    @Test
    void equalityIsSensitiveOnTableName() {
        assertNotEquals(new ColumnRef("p.d.t1", "a"), new ColumnRef("p.d.t2", "a"));
    }
}
