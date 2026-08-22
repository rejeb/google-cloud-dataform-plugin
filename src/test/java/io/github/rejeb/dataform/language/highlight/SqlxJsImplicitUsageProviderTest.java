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
import com.intellij.lang.javascript.inspections.JSUnusedLocalSymbolsInspection;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.ArrayList;
import java.util.List;

public class SqlxJsImplicitUsageProviderTest extends BasePlatformTestCase {

    private List<String> unusedDescriptions(String fileName, String sqlx) {
        myFixture.enableInspections(new JSUnusedLocalSymbolsInspection());
        myFixture.configureByText(fileName, sqlx);
        List<String> descriptions = new ArrayList<>();
        for (HighlightInfo info : myFixture.doHighlighting()) {
            String description = info.getDescription();
            if (description != null && description.startsWith("Unused")) {
                descriptions.add(description);
            }
        }
        return descriptions;
    }

    public void testSymbolUsedInATemplateExpressionIsNotReportedAsUnused() {
        String sqlx = "config { type: \"table\" }\n"
                + "js {\n"
                + "    const sampleCustomers = [1, 2, 3];\n"
                + "}\n"
                + "SELECT * FROM UNNEST([${sampleCustomers.join(\",\")}])\n";

        assertEquals("a symbol used in a template expression must not be reported as unused",
                List.of(), unusedDescriptions("used_symbol.sqlx", sqlx));
    }

    public void testFunctionUsedInATemplateExpressionIsNotReportedAsUnused() {
        String sqlx = "config { type: \"table\" }\n"
                + "js {\n"
                + "    function buildColumns() { return \"1\"; }\n"
                + "}\n"
                + "SELECT ${buildColumns()}\n";

        assertEquals("a function used in a template expression must not be reported as unused",
                List.of(), unusedDescriptions("used_function.sqlx", sqlx));
    }

    public void testSymbolNeverUsedIsStillReportedAsUnused() {
        String sqlx = "config { type: \"table\" }\n"
                + "js {\n"
                + "    const orphanSymbol = [1, 2, 3];\n"
                + "}\n"
                + "SELECT 1\n";

        assertFalse("a symbol that is never referenced must still be reported as unused",
                unusedDescriptions("unused_symbol.sqlx", sqlx).isEmpty());
    }

    public void testSymbolOnlyMentionedInsideAnIdentifierIsStillReportedAsUnused() {
        String sqlx = "config { type: \"table\" }\n"
                + "js {\n"
                + "    const orphan = 1;\n"
                + "}\n"
                + "SELECT ${orphanSymbol}\n";

        assertFalse("a partial name match must not count as a usage",
                unusedDescriptions("partial_match.sqlx", sqlx).isEmpty());
    }
}
