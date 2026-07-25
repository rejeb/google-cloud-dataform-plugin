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
package io.github.rejeb.dataform.language.evaluation;

import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class DataformMultilineTemplateCollectorTest extends BasePlatformTestCase {

    public void testMultilineTemplateExpressionsAreCollected() throws Exception {
        String text = Files.readString(Path.of("src/test/testData/folding/team_players_stat.sqlx"));
        PsiFile file = myFixture.addFileToProject("definitions/marts/team_players_stat.sqlx", text);

        List<DataformExpression> expressions = DataformExpressionCollector.collectSqlxTemplates(file);

        System.out.println("COLLECTED=" + expressions.size());
        for (DataformExpression e : expressions) {
            System.out.println("SRC[" + e.source().replace("\n", "\\n") + "]");
        }
        assertEquals("expected 2 ref() and 7 build_player_stats calls", 9, expressions.size());
    }
}
