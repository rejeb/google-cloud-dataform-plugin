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
package io.github.rejeb.dataform.language.unittest.completion;

import com.intellij.codeInsight.completion.CompletionUtilCore;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.List;
import java.util.Optional;

public class TestAliasSlotTest extends BasePlatformTestCase {

    private static final String DUMMY = CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED;

    private Optional<TestAliasSlot> slot(String sql) {
        PsiFile host = myFixture.configureByText("t_test.sqlx",
                "config {\n  type: \"test\",\n  dataset: \"orders\"\n}\n\n" + sql.replace("|", DUMMY) + "\n");
        int offset = host.getText().indexOf(DUMMY);
        PsiElement leaf = InjectedLanguageManager.getInstance(getProject()).findInjectedElementAt(host, offset);
        return TestAliasSlot.at(leaf);
    }

    public void testATopLevelAliasHasAnEmptyPathAndSeesItsSiblings() {
        TestAliasSlot slot = slot("SELECT 'a' AS order_id, 1 AS |").orElseThrow();
        assertEquals(List.of(), slot.recordPath());
        assertTrue(slot.isUsed("ORDER_ID"));
        assertFalse(slot.isUsed("total"));
    }

    public void testAStructFieldIsPathedByTheAliasOfItsStruct() {
        TestAliasSlot slot = slot("SELECT STRUCT('p' AS city, 1 AS |) AS address").orElseThrow();
        assertEquals(List.of("address"), slot.recordPath());
        assertTrue(slot.isUsed("city"));
    }

    public void testNestedStructsBuildTheFullPath() {
        assertEquals(List.of("address", "geo"),
                slot("SELECT STRUCT(STRUCT(0.0 AS |) AS geo) AS address").orElseThrow().recordPath());
    }

    public void testArraysAreTransparent() {
        assertEquals(List.of("items"), slot("SELECT [STRUCT('a' AS |)] AS items").orElseThrow().recordPath());
        assertEquals(List.of("items"),
                slot("SELECT ARRAY<STRUCT<sku STRING>>[STRUCT('a' AS |)] AS items").orElseThrow().recordPath());
    }

    public void testASelectAsStructFieldGivesNoSlot() {
        assertEquals(Optional.empty(), slot("SELECT ARRAY(SELECT AS STRUCT 'a' AS |) AS items"));
    }

    public void testAStructWithoutAliasGivesNoSlot() {
        assertEquals(Optional.empty(), slot("SELECT STRUCT('p' AS |)"));
    }

    public void testNoAsGivesNoSlot() {
        assertEquals(Optional.empty(), slot("SELECT |"));
    }

    public void testTheShapeOfTheAliasedValueIsKnown() {
        assertEquals(TestAliasSlot.ValueShape.STRUCT, slot("SELECT STRUCT('p' AS city) AS |").orElseThrow().shape());
        assertEquals(TestAliasSlot.ValueShape.ARRAY, slot("SELECT [1, 2] AS |").orElseThrow().shape());
        assertEquals(TestAliasSlot.ValueShape.SCALAR, slot("SELECT 'x' AS |").orElseThrow().shape());
    }
}
