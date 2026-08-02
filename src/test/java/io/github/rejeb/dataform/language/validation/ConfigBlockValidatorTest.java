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
package io.github.rejeb.dataform.language.validation;

import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class ConfigBlockValidatorTest extends BasePlatformTestCase {

    public void testValidConfigYieldsNoUnknownKeyProblems() {
        PsiFile file = myFixture.configureByText("a.sqlx", """
                config { type: "table" }

                SELECT 1
                """);
        for (SqlxValidationProblem problem : new ConfigBlockValidator().validate(file)) {
            assertNotSame(SqlxValidationProblem.Kind.UNKNOWN_CONFIG_KEY, problem.kind());
        }
    }

    public void testValidatorIsSilentWithoutASchema() {
        PsiFile file = myFixture.configureByText("b.sqlx", """
                config { totallyUnknownKey: "x" }

                SELECT 1
                """);
        assertNotNull(new ConfigBlockValidator().validate(file));
    }

    public void testNonSqlxFileYieldsNoProblems() {
        PsiFile file = myFixture.configureByText("notes.txt", "config { type: 1 }\n");
        assertEmpty(new ConfigBlockValidator().validate(file));
    }

    public void testFileWithoutConfigBlockYieldsNoProblems() {
        PsiFile file = myFixture.configureByText("c.sqlx", "SELECT 1\n");
        assertEmpty(new ConfigBlockValidator().validate(file));
    }
}
