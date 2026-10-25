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
package io.github.rejeb.dataform.language.unittest.columns;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.schema.sql.SqlPsiParts;

import java.util.List;
import java.util.Optional;

public class TestAliasPathsTest extends BasePlatformTestCase {

    private Optional<List<String>> pathOfAlias(String sql, String alias) {
        PsiFile host = myFixture.configureByText("t_test.sqlx",
                "config {\n  type: \"test\",\n  dataset: \"orders\"\n}\n\n" + sql + "\n");
        int offset = host.getText().lastIndexOf(" " + alias) + 1;
        PsiElement leaf = InjectedLanguageManager.getInstance(getProject()).findInjectedElementAt(host, offset);
        assertNotNull(leaf);
        for (PsiElement e = leaf; e != null && !(e instanceof PsiFile); e = e.getParent()) {
            if (SqlPsiParts.isType(e, TestAliasPaths.AS_EXPRESSION)
                    && TestAliasPaths.aliasIdentifier(e) != null
                    && com.intellij.psi.util.PsiTreeUtil.isAncestor(TestAliasPaths.aliasIdentifier(e), leaf, false)) {
                return TestAliasPaths.pathOf(e);
            }
        }
        return Optional.empty();
    }

    public void testATopLevelAliasIsItsOwnPath() {
        assertEquals(Optional.of(List.of("a")), pathOfAlias("SELECT 1 AS a", "a"));
    }

    public void testAStructFieldIsPathedByItsStruct() {
        assertEquals(Optional.of(List.of("address", "city")),
                pathOfAlias("SELECT STRUCT('p' AS city) AS address", "city"));
        assertEquals(Optional.of(List.of("address")),
                pathOfAlias("SELECT STRUCT('p' AS city) AS address", "address"));
    }

    public void testNestedStructsAndArraysBuildTheFullPath() {
        assertEquals(Optional.of(List.of("address", "geo", "lat")),
                pathOfAlias("SELECT STRUCT(STRUCT(0.0 AS lat) AS geo) AS address", "lat"));
        assertEquals(Optional.of(List.of("items", "sku")),
                pathOfAlias("SELECT [STRUCT('a' AS sku)] AS items", "sku"));
    }

    public void testAFieldOfAStructWithoutAliasHasNoPath() {
        assertEquals(Optional.empty(), pathOfAlias("SELECT STRUCT('p' AS city)", "city"));
    }

    public void testATableAliasIsNoColumn() {
        assertEquals(Optional.empty(), pathOfAlias("SELECT x FROM UNNEST([1]) AS t", "t"));
    }
}
