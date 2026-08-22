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

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.psi.PsiFile;
import com.intellij.sql.inspections.SqlResolveInspection;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Contributing candidates must never make a name stop resolving. The extension is additive and
 * suppresses nothing, so no column of the fixtures may be reported unresolved.
 */
public class ColumnReferenceInspectionGuardTest extends DataformProjectFixture {

    private Set<String> unresolvedNames(String path) throws Exception {
        myFixture.enableInspections(new SqlResolveInspection());
        PsiFile file = open(path);
        Set<String> unresolved = new LinkedHashSet<>();
        for (HighlightInfo info : myFixture.doHighlighting()) {
            String description = info.getDescription();
            if (description == null) continue;
            if (description.toLowerCase().contains("unable to resolve")) {
                unresolved.add(file.getText().substring(info.getStartOffset(), info.getEndOffset()));
            }
        }
        return unresolved;
    }

    public void testBronzeColumnsAllResolve() throws Exception {
        Set<String> unresolved = unresolvedNames("bronze/bronze_orders.sqlx");
        for (String column : Set.of("order_id", "customer_id", "order_ts", "raw_status")) {
            assertFalse("'" + column + "' must resolve, unresolved were " + unresolved,
                    unresolved.contains(column));
        }
    }

    public void testSilverColumnsAllResolve() throws Exception {
        Set<String> unresolved = unresolvedNames("silver/silver_orders.sqlx");
        for (String column : Set.of("order_id", "customer_id", "order_ts", "order_status")) {
            assertFalse("'" + column + "' must resolve, unresolved were " + unresolved,
                    unresolved.contains(column));
        }
    }

    public void testGoldColumnsAllResolve() throws Exception {
        Set<String> unresolved = unresolvedNames("gold/gold_customer_ltv.sqlx");
        for (String column : Set.of("order_id", "customer_id", "order_amount", "lifetime_value")) {
            assertFalse("'" + column + "' must resolve, unresolved were " + unresolved,
                    unresolved.contains(column));
        }
    }
}
