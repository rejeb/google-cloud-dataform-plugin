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
package io.github.rejeb.dataform.language.reference;

import com.google.gson.Gson;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.testing.ProjectStateInstaller;

import java.util.Arrays;
import java.util.List;

/**
 * Resolution and completion of the table name given to {@code ref()} and {@code resolve()},
 * against a compiled graph installed directly in the compilation service.
 */
public class DataformRefFunctionReferenceTest extends BasePlatformTestCase {

    private static final String GRAPH = """
            {
              "tables": [
                {"type": "table", "target": {"database": "p", "schema": "d", "name": "orders"},
                 "fileName": "definitions/orders.sqlx", "disabled": false},
                {"type": "view", "target": {"database": "p", "schema": "d", "name": "hidden"},
                 "fileName": "definitions/hidden.sqlx", "disabled": true}
              ],
              "declarations": [
                {"target": {"database": "p", "schema": "raw", "name": "events"},
                 "fileName": "definitions/sources.js"}
              ],
              "assertions": [
                {"target": {"database": "p", "schema": "d", "name": "orders_unique"},
                 "fileName": "definitions/checks.sqlx"}
              ],
              "operations": [
                {"target": {"database": "p", "schema": "d", "name": "cleanup"},
                 "fileName": "definitions/ops.sqlx"}
              ]
            }""";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        installGraph(new Gson().fromJson(GRAPH, CompiledGraph.class));
        myFixture.addFileToProject("definitions/orders.sqlx", "SELECT 1");
        myFixture.addFileToProject("definitions/sources.js", "declare({name: \"events\"});");
        myFixture.addFileToProject("definitions/checks.sqlx", "SELECT 1");
        myFixture.addFileToProject("definitions/ops.sqlx", "SELECT 1");
    }

    public void testRefResolvesToTheTableFile() {
        assertResolvesTo("ref(\"ord<caret>ers\")", "orders.sqlx");
    }

    public void testResolveFunctionAlsoResolves() {
        assertResolvesTo("resolve(\"ord<caret>ers\")", "orders.sqlx");
    }

    public void testRefResolvesDeclarationsAssertionsAndOperations() {
        assertResolvesTo("ref(\"eve<caret>nts\")", "sources.js");
        assertResolvesTo("ref(\"orders_un<caret>ique\")", "checks.sqlx");
        assertResolvesTo("ref(\"clea<caret>nup\")", "ops.sqlx");
    }

    public void testUnknownActionDoesNotResolve() {
        PsiReference reference = referenceAt("ref(\"no<caret>pe\")");
        assertNotNull(reference);
        assertNull(reference.resolve());
    }

    public void testKnownActionWithoutFileDoesNotResolve() {
        myFixture.addFileToProject("definitions/dangling.sqlx", "SELECT 1");
        installGraph(new Gson().fromJson("""
                {"tables": [{"type": "table", "target": {"database": "p", "schema": "d", "name": "ghost"},
                 "fileName": "definitions/missing.sqlx"}]}""", CompiledGraph.class));
        PsiReference reference = referenceAt("ref(\"gho<caret>st\")");
        assertNotNull(reference);
        assertNull(reference.resolve());
    }

    public void testOtherFunctionsGetNoDataformReference() {
        assertFalse(referenceAt("other(\"ord<caret>ers\")") instanceof DataformRefFunctionReference);
        assertTrue(referenceAt("ref(\"ord<caret>ers\")") instanceof DataformRefFunctionReference);
    }

    public void testWithoutACompiledGraphNothingResolves() {
        installGraph(null);
        PsiReference reference = referenceAt("ref(\"ord<caret>ers\")");
        assertNotNull(reference);
        assertNull(reference.resolve());
        assertEquals(0, reference.getVariants().length);
    }

    public void testReferenceRangeCoversTheNameWithoutQuotes() {
        PsiReference reference = referenceAt("ref(\"ord<caret>ers\")");
        assertNotNull(reference);
        assertEquals("orders", reference.getRangeInElement().substring(reference.getElement().getText()));
    }

    public void testVariantsOfferEnabledTablesAndDeclarations() {
        PsiReference reference = referenceAt("ref(\"<caret>\")");
        assertNotNull(reference);
        List<String> names = Arrays.stream(reference.getVariants())
                .map(v -> ((LookupElement) v).getLookupString())
                .toList();
        assertEquals(List.of("orders", "events"), names);
    }

    public void testASchemaQualifiedRefResolvesToTheTableOfThatSchema() {
        installTwoOrdersTables();
        assertResolvesToPath("ref(\"mart\", \"ord<caret>ers\")", "definitions/mart/orders.sqlx");
        assertResolvesToPath("ref(\"d\", \"ord<caret>ers\")", "definitions/orders.sqlx");
    }

    public void testADatabaseAndSchemaQualifiedRefResolves() {
        installTwoOrdersTables();
        assertResolvesToPath("ref(\"p\", \"mart\", \"ord<caret>ers\")", "definitions/mart/orders.sqlx");
    }

    public void testAnObjectRefResolvesToTheTableOfItsSchema() {
        installTwoOrdersTables();
        assertResolvesToPath("ref({schema: \"mart\", name: \"ord<caret>ers\"})", "definitions/mart/orders.sqlx");
    }

    public void testAnArrayRefResolvesToTheTableOfItsSchema() {
        installTwoOrdersTables();
        assertResolvesToPath("ref([\"mart\", \"ord<caret>ers\"])", "definitions/mart/orders.sqlx");
        assertResolvesToPath("ref([\"p\", \"mart\", \"ord<caret>ers\"])", "definitions/mart/orders.sqlx");
        assertResolvesToPath("ref([\"ord<caret>ers\"])", "definitions/orders.sqlx");
    }

    public void testAnObjectRefWithDatasetAndProjectKeysResolves() {
        installTwoOrdersTables();
        assertResolvesToPath("ref({project: \"p\", dataset: \"mart\", name: \"ord<caret>ers\"})",
                "definitions/mart/orders.sqlx");
    }

    public void testQualifiersOfArrayAndObjectRefsAreNoTableReference() {
        assertFalse(referenceAt("ref([\"ra<caret>w\", \"events\"])") instanceof DataformRefFunctionReference);
        assertFalse(referenceAt("ref({dataset: \"ra<caret>w\", name: \"events\"})") instanceof DataformRefFunctionReference);
    }

    public void testAnArrayFollowedByMoreArgumentsIsNoReference() {
        assertFalse(referenceAt("ref([\"raw\"], \"eve<caret>nts\")") instanceof DataformRefFunctionReference);
        assertFalse(referenceAt("ref([\"raw\", \"eve<caret>nts\"], \"x\")") instanceof DataformRefFunctionReference);
    }

    public void testASchemaQualifiedRefResolvesDeclarations() {
        assertResolvesTo("ref(\"raw\", \"eve<caret>nts\")", "sources.js");
    }

    public void testARefQualifiedByAnotherSchemaDoesNotResolve() {
        PsiReference reference = referenceAt("ref(\"elsewhere\", \"ord<caret>ers\")");
        assertTrue(reference instanceof DataformRefFunctionReference);
        assertNull(reference.resolve());
    }

    public void testTheSchemaArgumentIsNoTableReference() {
        assertFalse(referenceAt("ref(\"ra<caret>w\", \"events\")") instanceof DataformRefFunctionReference);
        assertFalse(referenceAt("ref(\"p\", \"ra<caret>w\", \"events\")") instanceof DataformRefFunctionReference);
        assertFalse(referenceAt("ref({schema: \"ra<caret>w\", name: \"events\"})") instanceof DataformRefFunctionReference);
    }

    private void installTwoOrdersTables() {
        myFixture.addFileToProject("definitions/mart/orders.sqlx", "SELECT 1");
        installGraph(new Gson().fromJson("""
                {"tables": [
                  {"type": "table", "target": {"database": "p", "schema": "d", "name": "orders"},
                   "fileName": "definitions/orders.sqlx"},
                  {"type": "table", "target": {"database": "p", "schema": "mart", "name": "orders"},
                   "fileName": "definitions/mart/orders.sqlx"}
                ]}""", CompiledGraph.class));
    }

    private void assertResolvesToPath(String call, String expectedPath) {
        PsiReference reference = referenceAt(call);
        assertNotNull("no reference in " + call, reference);
        PsiElement resolved = reference.resolve();
        assertTrue(call + " must resolve to a file", resolved instanceof PsiFile);
        assertTrue(call + " resolved to " + ((PsiFile) resolved).getVirtualFile().getPath(),
                ((PsiFile) resolved).getVirtualFile().getPath().endsWith(expectedPath));
    }

    private void assertResolvesTo(String call, String expectedFile) {
        PsiReference reference = referenceAt(call);
        assertNotNull("no reference in " + call, reference);
        PsiElement resolved = reference.resolve();
        assertTrue(call + " must resolve to a file", resolved instanceof PsiFile);
        assertEquals(expectedFile, ((PsiFile) resolved).getName());
    }

    private PsiReference referenceAt(String call) {
        myFixture.configureByText("query.js", call + ";");
        return myFixture.getReferenceAtCaretPosition();
    }

    private void installGraph(CompiledGraph graph) {
        ProjectStateInstaller.installGraph(getProject(), getTestRootDisposable(), graph);
    }
}
