/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
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
package io.github.rejeb.dataform.language.parser;

import com.intellij.psi.PsiFile;
import com.intellij.testFramework.ParsingTestCase;
import io.github.rejeb.dataform.language.SqlxLanguage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SqlxParserTest extends ParsingTestCase {

    public SqlxParserTest() {
        super("", "sqlx", new SqlxParserDefinition());
    }

    @Override
    protected String getTestDataPath() {
        return "src/test/testData";
    }

    @Override
    protected boolean skipSpaces() {
        return false;
    }

    @Override
    protected boolean includeRanges() {
        return true;
    }

    public void testParseConfigBlock() {
        String content = """
                config {
                    type: "table",
                    schema: "my_schema"
                }
                """;

        PsiFile file = createPsiFile("test", content);
        assertNotNull(file);
        assertEquals(SqlxLanguage.INSTANCE, file.getLanguage());
    }

    public void testParseJsBlock() {
        String content = """
                js {
                    const myVar = 10;
                    function myFunc() { return 42; }
                }
                """;

        PsiFile file = createPsiFile("test", content);
        assertNotNull(file);
        assertEquals(SqlxLanguage.INSTANCE, file.getLanguage());
    }

    public void testParseSqlBlock() {
        String content = """
                SELECT * FROM table
                WHERE id = 1
                """;

        PsiFile file = createPsiFile("test", content);
        assertNotNull(file);
        assertEquals(SqlxLanguage.INSTANCE, file.getLanguage());
    }

    public void testParseCompleteFile() {
        String content = """
                config {
                    type: "table",
                    schema: "public"
                }

                js {
                    const tableName = "users";
                }

                SELECT * FROM ${tableName}
                """;

        PsiFile file = createPsiFile("test", content);
        assertNotNull(file);
        assertEquals(SqlxLanguage.INSTANCE, file.getLanguage());
    }

    public void testParseMultipleJsBlocks() {
        String content = """
                config { type: "table" }

                js {
                    const first = 1;
                }

                js {
                    const second = 2;
                }

                SELECT ${first}, ${second}
                """;

        PsiFile file = createPsiFile("test", content);
        assertNotNull(file);
        assertEquals(SqlxLanguage.INSTANCE, file.getLanguage());
    }

    public void testParseConfigWithNestedObject() {
        String content = """
                config {
                    type: "table",
                    bigquery: {
                        partitionBy: "date",
                        clusterBy: ["id", "name"]
                    }
                }
                """;

        PsiFile file = createPsiFile("test", content);
        assertNotNull(file);
        assertEquals(SqlxLanguage.INSTANCE, file.getLanguage());
    }

    public void testParseEmptyFile() {
        String content = "";

        PsiFile file = createPsiFile("test", content);
        assertNotNull(file);
        assertEquals(SqlxLanguage.INSTANCE, file.getLanguage());
    }

    public void testParseOnlyConfig() {
        String content = """
                config {
                    type: "view"
                }
                """;

        PsiFile file = createPsiFile("test", content);
        assertNotNull(file);
        assertEquals(SqlxLanguage.INSTANCE, file.getLanguage());
    }

    public void testParseWithComments() {
        String content = """
                -- This is a comment
                config {
                    type: "table"
                }

                /* Multi-line
                   comment */
                SELECT * FROM table
                """;

        PsiFile file = createPsiFile("test", content);
        assertNotNull(file);
        assertEquals(SqlxLanguage.INSTANCE, file.getLanguage());
    }

    public void testParseConfigWithStringValues() {
        String content = """
                config {
                    type: "table",
                    description: "This is a table description",
                    tags: ["tag1", "tag2"]
                }
                """;

        PsiFile file = createPsiFile("test", content);
        assertNotNull(file);
        assertEquals(SqlxLanguage.INSTANCE, file.getLanguage());
    }
}
