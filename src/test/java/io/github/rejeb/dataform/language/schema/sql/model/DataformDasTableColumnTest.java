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
package io.github.rejeb.dataform.language.schema.sql.model;

import com.intellij.database.model.DasObject;
import com.intellij.database.model.ObjectKind;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.List;

public class DataformDasTableColumnTest extends BasePlatformTestCase {

    private DataformDasColumn firstColumn() {
        DataformDasTable table = new DataformDasTable(getPsiManager(), "team_players_stat",
                List.of(new ColumnInfo("goalsScored", "INT64", "NULLABLE", null)), null);
        DasObject child = table.getDasChildren(ObjectKind.COLUMN).first();
        assertTrue(child instanceof DataformDasColumn);
        return (DataformDasColumn) child;
    }

    public void testChildColumnHasNonNullTable() {
        // A null table makes the platform join smart-completion (FuzzyKey#getRefTable) throw an NPE.
        assertNotNull("child column must expose its owning table to avoid the join-completion NPE",
                firstColumn().getTable());
    }

    public void testChildColumnLocationStringIsNull() {
        // A null location string keeps SQL completion from qualifying the insert as table.column.
        assertNull("column completion must insert an unqualified name",
                firstColumn().getPresentation().getLocationString());
    }
}
