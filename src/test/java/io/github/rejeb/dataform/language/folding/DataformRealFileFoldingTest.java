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

import com.intellij.openapi.editor.CustomFoldRegion;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.psi.PsiFile;
import io.github.rejeb.dataform.language.evaluation.DataformExpression;
import io.github.rejeb.dataform.language.evaluation.DataformExpressionCollector;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public class DataformRealFileFoldingTest extends DataformFoldingTestCase {

    private static final String LONG_VALUE = """
            
                STRUCT(
                  MAX(goalsScored) AS goalsScored,
                  ARRAY_AGG(
                    IF(goalsScored = 0 OR goalsScored = 0.00, NULL,
                      STRUCT(appearances, brandSponsorAndUsed, club, position, playerDob, playerName))
                    ORDER BY goalsScored DESC LIMIT 1
                  )[OFFSET(0)] AS players
                )
            """;

    public void testMultilineTemplateExpressionsFold() throws Exception {
        String text = Files.readString(Path.of("src/test/testData/folding/team_players_stat.sqlx"));
        PsiFile file = myFixture.addFileToProject("definitions/marts/team_players_stat.sqlx", text);
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());

        List<DataformExpression> expressions = DataformExpressionCollector.collectSqlxTemplates(file);
        for (DataformExpression expression : expressions) {
            seed(file, expression.source(), expression.source().startsWith("ref")
                    ? "project.dataset.team_players_stat_raw_cleaned"
                    : LONG_VALUE);
        }

        List<FoldRegion> inline = dataformRegions();
        DataformMultilineFoldManager.apply(myFixture.getEditor(),
                DataformMultilineValues.of(getProject(), file.getVirtualFile(),
                        myFixture.getEditor().getDocument()));
        long multiline = Arrays.stream(myFixture.getEditor().getFoldingModel().getAllFoldRegions())
                .filter(region -> region instanceof CustomFoldRegion)
                .count();

        assertEquals("the two inline ref() calls fold to one line, got " + inline, 2, inline.size());
        assertEquals("the seven whole-line calls are painted over several lines", 7, multiline);
    }
}
