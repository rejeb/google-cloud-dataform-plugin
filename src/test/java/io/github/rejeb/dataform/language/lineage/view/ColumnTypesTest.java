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
package io.github.rejeb.dataform.language.lineage.view;

import com.intellij.psi.PsiManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.lineage.model.LineageModel;
import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;
import io.github.rejeb.dataform.language.schema.sql.model.DataformDasTable;

import java.util.List;
import java.util.Map;

public class ColumnTypesTest extends BasePlatformTestCase {

    private DataformDasTable table(String fullName, ColumnInfo... columns) {
        return new DataformDasTable(PsiManager.getInstance(getProject()), fullName, "t", List.of(columns), null);
    }

    public void testEveryColumnOfATableWithAFullNameHasItsType() {
        Map<String, String> types = ColumnTypes.of(List.of(table("p.d.orders",
                new ColumnInfo("Order_Id", "STRING", "NULLABLE", null), new ColumnInfo("amount", "NUMERIC", "NULLABLE", null))));

        assertEquals("STRING", types.get(LineageModel.columnTypeKey("p.d.orders", "order_id")));
        assertEquals("NUMERIC", types.get(LineageModel.columnTypeKey("p.d.orders", "amount")));
        assertEquals(2, types.size());
    }

    public void testATableWithoutAFullNameGivesNoType() {
        assertTrue(ColumnTypes.of(List.of(table(null, new ColumnInfo("id", "STRING", "NULLABLE", null)))).isEmpty());
    }
}
