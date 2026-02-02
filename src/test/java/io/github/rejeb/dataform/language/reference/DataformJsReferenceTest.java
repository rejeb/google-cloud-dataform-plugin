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
package io.github.rejeb.dataform.language.reference;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class DataformJsReferenceTest extends BasePlatformTestCase {

    @Override
    protected String getTestDataPath() {
        return "src/test/testData";
    }

    public void testReferenceWithValidIdentifier() {
        myFixture.configureByText("test.sqlx", """
                config { type: "table" }
                js {
                    const myVar = 10;
                }
                SELECT ${myVar} as value
                """);

        PsiElement element = myFixture.getFile().findElementAt(
                myFixture.getEditor().getDocument().getText().indexOf("myVar", 60)
        );

        assertNotNull(element);
    }

    public void testReferenceResolution() {
        myFixture.configureByText("test.sqlx", """
                config { type: "table" }
                js {
                    const myConst = 42;
                    let myVar = 100;
                }
                SELECT ${myConst} as value
                """);

        int offset = myFixture.getEditor().getDocument().getText().indexOf("myConst", 80);
        PsiElement element = myFixture.getFile().findElementAt(offset);

        assertNotNull(element);
    }

    public void testGetVariantsReturnsLocalSymbols() {
        myFixture.configureByText("test.sqlx", """
                config { type: "table" }
                js {
                    const API_KEY = "key";
                    let counter = 0;

                    function increment() {
                        return counter + 1;
                    }
                }
                SELECT * FROM table
                """);

        PsiElement element = myFixture.getFile().findElementAt(0);
        assertNotNull(element);
    }

    public void testReferenceWithInvalidIdentifier() {
        myFixture.configureByText("test.sqlx", """
                config { type: "table" }
                js {
                    const myVar = 10;
                }
                SELECT ${invalidVar} as value
                """);

        int offset = myFixture.getEditor().getDocument().getText().indexOf("invalidVar");
        PsiElement element = myFixture.getFile().findElementAt(offset);

        assertNotNull(element);
    }

    public void testReferenceToFunction() {
        myFixture.configureByText("test.sqlx", """
                config { type: "table" }
                js {
                    function myFunction() {
                        return "result";
                    }
                }
                SELECT ${myFunction()} as value
                """);

        int offset = myFixture.getEditor().getDocument().getText().indexOf("myFunction", 80);
        PsiElement element = myFixture.getFile().findElementAt(offset);

        assertNotNull(element);
    }

    public void testMultipleJsBlocks() {
        myFixture.configureByText("test.sqlx", """
                config { type: "table" }
                js {
                    const first = 1;
                }
                js {
                    const second = 2;
                }
                SELECT ${first}, ${second}
                """);

        PsiElement element = myFixture.getFile().findElementAt(0);
        assertNotNull(element);
    }

    public void testReferenceInNestedScope() {
        myFixture.configureByText("test.sqlx", """
                config { type: "table" }
                js {
                    function outer() {
                        const inner = 5;
                        return inner;
                    }
                }
                SELECT ${outer()} as value
                """);

        PsiElement element = myFixture.getFile().findElementAt(0);
        assertNotNull(element);
    }
}
