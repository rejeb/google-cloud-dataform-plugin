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
package io.github.rejeb.dataform.language.unittest;

import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.Optional;

public class SqlxUnitTestsTest extends BasePlatformTestCase {

    private PsiFile sqlx(String text) {
        return myFixture.addFileToProject("definitions/a" + Integer.toHexString(text.hashCode()) + ".sqlx", text);
    }

    public void testATestConfigIsATestFile() {
        assertTrue(SqlxUnitTests.isUnitTestFile(sqlx("config {\n  type: \"test\",\n  dataset: \"orders\"\n}\nSELECT 1\n")));
        assertTrue(SqlxUnitTests.isUnitTestFile(sqlx("config { type:'test' }\nSELECT 1\n")));
    }

    public void testOtherTypesAreNotTestFiles() {
        assertFalse(SqlxUnitTests.isUnitTestFile(sqlx("config { type: \"table\" }\nSELECT 'test'\n")));
        assertFalse(SqlxUnitTests.isUnitTestFile(sqlx("config { type: \"testing\" }\nSELECT 1\n")));
        assertFalse(SqlxUnitTests.isUnitTestFile(sqlx("config { subtype: \"test\" }\nSELECT 1\n")));
        assertFalse(SqlxUnitTests.isUnitTestFile(sqlx("SELECT 1\n")));
    }

    public void testTheTestedDatasetIsReadFromAStringOrATarget() {
        assertEquals(Optional.of("orders"), SqlxUnitTests.testedDatasetName(
                sqlx("config {\n  type: \"test\",\n  dataset: \"orders\"\n}\nSELECT 1\n")));
        assertEquals(Optional.of("orders"), SqlxUnitTests.testedDatasetName(
                sqlx("config {\n  type: \"test\",\n  dataset: {schema: \"d\", name: \"orders\"}\n}\nSELECT 1\n")));
        assertEquals(Optional.empty(), SqlxUnitTests.testedDatasetName(
                sqlx("config {\n  type: \"table\",\n  dataset: \"orders\"\n}\nSELECT 1\n")));
    }
}
