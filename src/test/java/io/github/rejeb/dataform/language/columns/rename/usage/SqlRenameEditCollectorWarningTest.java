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
package io.github.rejeb.dataform.language.columns.rename.usage;

import io.github.rejeb.dataform.language.columns.model.ColumnRef;
import io.github.rejeb.dataform.language.columns.rename.ColumnRenameFixture;

import java.util.List;
import java.util.Set;

/**
 * Why a column of the rename keeps its old name: the file building it is missing, or the file is
 * there and holds no select-list item naming the column.
 */
public class SqlRenameEditCollectorWarningTest extends ColumnRenameFixture {

    private static final ColumnRef COLUMN = new ColumnRef("p.d.ops", "customer_id");

    private List<String> warnings() {
        return SqlRenameEditCollector.collect(getProject(), Set.of(COLUMN), Set.of(), Set.of(), "cid").warnings();
    }

    public void testAColumnNoSelectListDeclaresSaysSoAndNamesTheFile() {
        installProject();
        addOperation("ops");
        addFile("ops", "config { type: \"operations\", hasOutput: true }\n\n"
                + "CREATE TABLE `p.d.ops` (customer_id INT64)\n");

        assertEquals(List.of("p.d.ops.customer_id has no select-list item in ops.sqlx, so it keeps the old name."
                + " Rename it there by hand."), warnings());
    }

    public void testAMissingFileIsToldAsSuch() {
        installProject();
        addOperation("ops");

        assertEquals(List.of("The file declaring p.d.ops.customer_id could not be opened, so it keeps the old name"),
                warnings());
    }
}
