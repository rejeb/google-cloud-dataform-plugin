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
package io.github.rejeb.dataform.language.columns.rename;

import com.intellij.psi.PsiFile;
import io.github.rejeb.dataform.language.columns.rename.usage.SqlxStarDeclarationLocator;

/**
 * The aliases a starred query takes its column names from are those outside its select list, which
 * has to be found through every wrapper of the main query.
 */
public class SqlxStarDeclarationLocatorTest extends ColumnRenameFixture {

    public void testAStructAliasReadThroughAStarIsFound() {
        PsiFile file = addFile("src", "config { type: \"table\" }\n\n"
                + "SELECT * FROM UNNEST([STRUCT(1 AS customer_id)])\n");

        assertSize(1, SqlxStarDeclarationLocator.findStructAliases(file, "customer_id"));
    }

    public void testTheSelectListOfAParenthesizedMainQueryIsNotAStructAlias() {
        PsiFile file = addFile("src", "config { type: \"table\" }\n\n(SELECT 1 AS customer_id)\n");

        assertEmpty(SqlxStarDeclarationLocator.findStructAliases(file, "customer_id"));
    }

    public void testTheSelectListOfAUnionIsNotAStructAlias() {
        PsiFile file = addFile("src", "config { type: \"table\" }\n\n"
                + "SELECT 1 AS customer_id\nUNION ALL\nSELECT 2 AS other\n");

        assertEmpty(SqlxStarDeclarationLocator.findStructAliases(file, "customer_id"));
    }
}
