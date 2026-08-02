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
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DataformDeclaredVariableResolveTest extends BasePlatformTestCase {

    /**
     * Names flagged unresolved by the SQL resolve inspection. The file is configured once per
     * call, so a test asserting on several names must call this once and query the result:
     * configuring the same file name repeatedly leaves more than one declaration visible to the
     * resolver and makes struct-field resolution non-idempotent.
     */
    private Set<String> unresolvedNames(String sqlx) {
        myFixture.enableInspections(new SqlResolveInspection());
        PsiFile file = myFixture.configureByText("test_create.sqlx", sqlx);
        Set<String> unresolved = new HashSet<>();
        for (HighlightInfo hi : myFixture.doHighlighting()) {
            String desc = hi.getDescription();
            if (desc == null) continue;
            if (desc.toLowerCase().contains("unable to resolve")) {
                unresolved.add(file.getText().substring(hi.getStartOffset(), hi.getEndOffset()));
            }
        }
        return unresolved;
    }

    private boolean hasUnresolved(String sqlx, String name) {
        return unresolvedNames(sqlx).contains(name);
    }

    public void testSimpleDeclaredVariableResolves() {
        String sqlx = "config { type: \"incremental\" }\n"
                + "pre_operations {\nDECLARE countries ARRAY<STRING>;\n}\n"
                + "SELECT 1 FROM t WHERE teamName IN UNNEST(countries)";
        assertFalse("declared variable 'countries' must resolve, not be flagged unresolved",
                hasUnresolved(sqlx, "countries"));
    }

    public void testMultiNameDeclareResolves() {
        String sqlx = "config { type: \"table\" }\n"
                + "pre_operations {\nDECLARE lo, hi INT64;\n}\n"
                + "SELECT 1 FROM t WHERE x BETWEEN lo AND hi";
        Set<String> unresolved = unresolvedNames(sqlx);
        assertFalse("first name of a multi-name DECLARE must resolve", unresolved.contains("lo"));
        assertFalse("second name of a multi-name DECLARE must resolve", unresolved.contains("hi"));
    }

    public void testStructVariableFieldResolves() {
        String sqlx = "config { type: \"table\" }\n"
                + "pre_operations {\nDECLARE bounds STRUCT<lo INT64, hi INT64>;\n}\n"
                + "SELECT 1 FROM t WHERE x BETWEEN bounds.lo AND bounds.hi";
        Set<String> unresolved = unresolvedNames(sqlx);
        assertFalse("struct variable 'bounds' must resolve", unresolved.contains("bounds"));
        assertFalse("struct field 'bounds.lo' must resolve", unresolved.contains("lo"));
        assertFalse("struct field 'bounds.hi' must resolve", unresolved.contains("hi"));
    }

    public void testUndeclaredNameStillFlagged() {
        String sqlx = "config { type: \"table\" }\n"
                + "pre_operations {\nDECLARE countries ARRAY<STRING>;\n}\n"
                + "SELECT 1 FROM t WHERE x IN UNNEST(unknown_var)";
        assertTrue("a name that is not declared must still be flagged unresolved",
                hasUnresolved(sqlx, "unknown_var"));
    }

    public void testDeclaredVariableAppearsInCompletion() {
        String sqlx = "config { type: \"table\" }\n"
                + "pre_operations {\nDECLARE countries ARRAY<STRING>;\n}\n"
                + "SELECT 1 FROM t WHERE teamName IN UNNEST(countr<caret>)";
        myFixture.configureByText("test_create.sqlx", sqlx);
        assertCompletionOffers("countries");
    }

    public void testStructVariableAppearsInCompletion() {
        String sqlx = "config { type: \"table\" }\n"
                + "pre_operations {\nDECLARE bounds STRUCT<lo INT64, hi INT64>;\n}\n"
                + "SELECT 1 FROM t WHERE x > boun<caret>";
        myFixture.configureByText("test_create.sqlx", sqlx);
        assertCompletionOffers("bounds");
    }

    private void assertCompletionOffers(String name) {
        myFixture.completeBasic();
        java.util.List<String> lk = myFixture.getLookupElementStrings();
        if (lk == null) {
            assertTrue("single completion should have inserted '" + name + "', got: "
                            + myFixture.getFile().getText(),
                    myFixture.getFile().getText().contains(name));
        } else {
            assertTrue("'" + name + "' must appear in autocomplete, got " + lk, lk.contains(name));
        }
    }
}
