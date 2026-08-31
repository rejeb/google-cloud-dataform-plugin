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
package io.github.rejeb.dataform.language.highlight;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.sql.inspections.SqlResolveInspection;
import io.github.rejeb.dataform.language.refactoring.column.ColumnRenameFixture;

import java.util.List;

/**
 * A query reading rows a {@code js} block builds names columns no schema knows: the source is a
 * template hole, and what it expands to is decided when Dataform compiles. Such a name cannot be
 * checked, so it is not reported — while a name read from an action the project declares still is.
 */
public class SqlxTemplateSourceHighlightTest extends ColumnRenameFixture {

    private static final String BUILT_IN_JS = """
            config {
                type: "table",
                columns: {
                    id_customer: "Source customer identifier"
                }
            }

            js {
                const rows = [["Alice"]];
            }

            SELECT
            customer_id AS id_customer,
            full_name
            FROM UNNEST([
                ${rows.map(r => `STRUCT(1 AS customer_id, '${r[0]}' AS full_name)`).join(", ")}
            ])
            """;

    private List<String> problemsOf(String action) {
        myFixture.enableInspections(new SqlResolveInspection());
        myFixture.configureFromExistingVirtualFile(fileOf(action).getVirtualFile());
        List<String> problems = new java.util.ArrayList<>();
        for (HighlightInfo info : myFixture.doHighlighting()) {
            if (info.getDescription() != null && info.getDescription().contains("resolve column")) {
                problems.add(info.getSeverity() + ": " + info.getDescription());
            }
        }
        return problems;
    }

    public void testAColumnReadFromRowsBuiltInJavaScriptIsNotReported() {
        installProject(new Action("bronze",
                "SELECT * FROM UNNEST([STRUCT(1 AS id_customer, 'a' AS full_name)])",
                List.of(), List.of("id_customer", "full_name")));
        addFile("bronze", BUILT_IN_JS);

        assertEquals("what the query reads is built in JavaScript, so nothing here can be checked",
                List.of(), problemsOf("bronze"));
    }

    public void testAColumnMissingFromTheActionReadIsStillReported() {
        installProject(
                new Action("src", "SELECT 'a' AS full_name", List.of(), List.of("full_name")),
                new Action("fin", "SELECT nope FROM `p.d.src`", List.of("src"), List.of()));
        addFile("src", "config { type: \"table\" }\n\nSELECT 'a' AS full_name\n");
        addFile("fin", "config { type: \"table\" }\n\nSELECT nope AS other\nFROM ${ref(\"src\")}\n");

        assertEquals("a name the action read does not publish is still worth reporting",
                1, problemsOf("fin").size());
    }
}
