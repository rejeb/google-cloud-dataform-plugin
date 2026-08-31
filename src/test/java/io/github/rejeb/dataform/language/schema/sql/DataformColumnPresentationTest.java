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

import com.intellij.database.model.DasObject;
import com.intellij.database.model.ObjectKind;
import io.github.rejeb.dataform.language.lineage.column.ColumnRef;
import io.github.rejeb.dataform.language.schema.sql.model.DataformDasColumn;
import io.github.rejeb.dataform.language.schema.sql.model.DataformDasTable;

/**
 * A column offered to completion says nothing about where it lives.
 *
 * <p>The SQL plugin writes {@code table.column} instead of {@code column} for any column whose
 * presentation carries a location. A column of a table the query already reads must therefore carry
 * none, whichever of the two ways it was built: through the schema service, or through the table
 * handing out its children.</p>
 */
public class DataformColumnPresentationTest extends DataformProjectFixture {

    public void testAColumnBuiltFromTheSchemaCarriesNoLocation() {
        DataformDasColumn column = ColumnOriginService.getInstance(getProject())
                .dasColumn(new ColumnRef("proj.ds.bronze_orders", "order_id"));

        assertNotNull(column);
        assertNull("a location makes the SQL plugin qualify the insert",
                column.getPresentation().getLocationString());
    }

    public void testAColumnHandedOutByItsTableCarriesNoLocation() {
        DataformDasTable table = DataformTableSchemaService.getInstance(getProject())
                .getAllTables().get("proj.ds.bronze_orders");
        assertNotNull(table);

        for (DasObject child : table.getDasChildren(ObjectKind.COLUMN)) {
            assertTrue(child instanceof DataformDasColumn);
            assertNull("a location makes the SQL plugin qualify the insert of " + child.getName(),
                    ((DataformDasColumn) child).getPresentation().getLocationString());
        }
    }
}
