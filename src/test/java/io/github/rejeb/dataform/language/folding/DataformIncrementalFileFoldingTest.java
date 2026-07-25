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
package io.github.rejeb.dataform.language.folding;

import com.intellij.psi.PsiFile;
import io.github.rejeb.dataform.language.evaluation.DataformExpression;
import io.github.rejeb.dataform.language.evaluation.DataformExpressionCollector;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class DataformIncrementalFileFoldingTest extends DataformFoldingTestCase {

    private static final String HELPER = "schema_helpers.incrementalWhereClause(\"order_ts\", 3)";

    private PsiFile configureRealFile() throws Exception {
        String text = Files.readString(Path.of("src/test/testData/folding/silver_orders.sqlx"));
        return configureDefinition("silver/silver_orders.sqlx", text);
    }

    public void testOnlyDeterministicPartsOfTheFileAreCollected() throws Exception {
        PsiFile file = configureRealFile();

        List<String> sources = DataformExpressionCollector.collectSqlxTemplates(file).stream()
                .map(DataformExpression::source)
                .toList();

        assertEquals("got " + sources, List.of("self()", HELPER, "ref(\"bronze_orders\")", HELPER, "self()"), sources);
    }

    public void testWhenExpressionItselfIsNeverFolded() throws Exception {
        PsiFile file = configureRealFile();

        boolean foldsWhen = DataformExpressionCollector.collectSqlxTemplates(file).stream()
                .anyMatch(expression -> expression.source().contains("when("));

        assertFalse("when()/incremental() depend on the run mode and must stay visible", foldsWhen);
    }

    public void testNestedHelperFoldsWithoutHidingTheSurroundingSql() throws Exception {
        PsiFile file = configureRealFile();
        seed(file, HELPER, "order_ts >= TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 3 DAY)");
        seed(file, "self()", "proj.silver.silver_orders");
        seed(file, "ref(\"bronze_orders\")", "proj.bronze.bronze_orders");

        String text = myFixture.getEditor().getDocument().getText();
        dataformRegions().forEach(region -> {
            String covered = text.substring(region.getStartOffset(), region.getEndOffset());
            assertTrue("a fold must never cover when(): " + covered, !covered.contains("when("));
        });
        assertEquals("all five deterministic parts fold", 5, dataformRegions().size());
    }
}
