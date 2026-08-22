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
package io.github.rejeb.dataform.language.schema.sql.model;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.schema.sql.DataformTableSchemaService;

import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

/**
 * Find Usages on a Dataform table column must list the usages in every SQLX file, not only the
 * file the search starts from. The references in other files re-resolve during the search, so
 * the resolve targets must compare as the same logical column even when they are distinct
 * instances.
 */
public class DataformColumnFindUsagesTest extends BasePlatformTestCase {

    private static final String CACHE_JSON = """
            {"p.d.orders":{"columns":[{"name":"order_id","type":"STRING","mode":"NULLABLE",\
            "subFields":[]}],"lastModified":0,"fileName":"definitions/orders.sqlx"}}""";

    private void installSchema() {
        DataformTableSchemaService.State state = new DataformTableSchemaService.State();
        state.schemaCacheJson = CACHE_JSON;
        DataformTableSchemaService.getInstance(getProject()).loadState(state);
    }

    private PsiElement resolveColumn(PsiFile file) {
        int offset = file.getText().indexOf("order_id") + "order_".length();
        PsiElement injected = InjectedLanguageManager.getInstance(getProject())
                .findInjectedElementAt(file, offset);
        assertNotNull("the column must be inside the injected SQL", injected);
        PsiReference ref = injected.getContainingFile()
                .findReferenceAt(injected.getTextRange().getStartOffset());
        assertNotNull("the column must have a reference", ref);
        PsiElement target = ref.resolve();
        assertNotNull("the column reference must resolve", target);
        return target;
    }

    private PsiFile openDefinition(String name, String text) {
        PsiFile file = myFixture.addFileToProject("definitions/" + name, text);
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        return myFixture.getFile();
    }

    public void testColumnTargetsResolvedFromDifferentFilesAreEquivalent() {
        installSchema();
        PsiFile first = openDefinition("first.sqlx",
                "config { type: \"table\" }\nSELECT order_id FROM orders");
        PsiElement fromFirst = resolveColumn(first);

        PsiFile second = openDefinition("second.sqlx",
                "config { type: \"table\" }\nSELECT order_id FROM orders");
        PsiElement fromSecond = resolveColumn(second);

        assertTrue("the same logical column resolved from two files must be equivalent, "
                        + "otherwise isReferenceTo fails and cross-file usages are missed",
                getPsiManager().areElementsEquivalent(fromFirst, fromSecond));
    }

    public void testFindUsagesListsUsagesInEveryFile() {
        installSchema();
        myFixture.addFileToProject("definitions/other.sqlx",
                "config { type: \"table\" }\nSELECT order_id FROM orders");
        PsiFile file = openDefinition("current.sqlx",
                "config { type: \"table\" }\nSELECT order_id FROM orders");
        PsiElement target = resolveColumn(file);

        Collection<PsiReference> usages = ReferencesSearch
                .search(target, GlobalSearchScope.projectScope(getProject()))
                .findAll();
        Set<String> files = new TreeSet<>();
        InjectedLanguageManager ilm = InjectedLanguageManager.getInstance(getProject());
        for (PsiReference usage : usages) {
            files.add(ilm.getTopLevelFile(usage.getElement()).getName());
        }
        assertTrue("usages in the current file must be listed, got " + files,
                files.contains("current.sqlx"));
        assertTrue("usages in other files must be listed, got " + files,
                files.contains("other.sqlx"));
    }

    public void testTableUsagesAreListedInEveryFileToo() {
        installSchema();
        myFixture.addFileToProject("definitions/other.sqlx",
                "config { type: \"table\" }\nSELECT order_id FROM orders");
        PsiFile file = openDefinition("current.sqlx",
                "config { type: \"table\" }\nSELECT order_id FROM orders");

        int offset = file.getText().indexOf("FROM orders") + "FROM ".length();
        PsiElement injected = InjectedLanguageManager.getInstance(getProject())
                .findInjectedElementAt(file, offset);
        assertNotNull("the table must be inside the injected SQL", injected);
        PsiReference ref = injected.getContainingFile()
                .findReferenceAt(injected.getTextRange().getStartOffset());
        assertNotNull("the table must have a reference", ref);
        PsiElement target = ref.resolve();
        assertNotNull("the table reference must resolve", target);

        Set<String> files = new TreeSet<>();
        InjectedLanguageManager ilm = InjectedLanguageManager.getInstance(getProject());
        for (PsiReference usage : ReferencesSearch
                .search(target, GlobalSearchScope.projectScope(getProject())).findAll()) {
            files.add(ilm.getTopLevelFile(usage.getElement()).getName());
        }
        assertTrue("table usages in the current file must be listed, got " + files,
                files.contains("current.sqlx"));
        assertTrue("table usages in other files must be listed, got " + files,
                files.contains("other.sqlx"));
    }
}
