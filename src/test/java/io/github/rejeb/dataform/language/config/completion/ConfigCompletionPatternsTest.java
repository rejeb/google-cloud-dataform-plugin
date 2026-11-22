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
package io.github.rejeb.dataform.language.config.completion;

import com.intellij.codeInsight.completion.CompletionUtilCore;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.ProcessingContext;
import io.github.rejeb.dataform.language.config.ConfigColumnSlots;

public class ConfigCompletionPatternsTest extends BasePlatformTestCase {

    private static final String D = CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED;

    private final ProcessingContext context = new ProcessingContext();

    public void testInConfigBlockAcceptsAConfigValue() {
        assertTrue(accepts(ConfigCompletionPatterns.inConfigBlock(), sqlx("config {\n  description: \"" + D + "\"\n}")));
    }

    public void testInConfigBlockRejectsAJsBlock() {
        assertFalse(accepts(ConfigCompletionPatterns.inConfigBlock(),
                sqlx("config { type: \"table\" }\njs {\n  const a = " + D + ";\n}")));
    }

    public void testInConfigBlockRejectsAPlainJsFile() {
        PsiFile file = myFixture.addFileToProject("includes/helpers.js", "const a = " + D + ";\n");
        PsiElement leaf = file.findElementAt(file.getText().indexOf(D));
        assertNotNull(leaf);

        assertFalse(accepts(ConfigCompletionPatterns.inConfigBlock(), leaf));
    }

    public void testTagsArrayValueAcceptsAStringInTheTagsArray() {
        assertTrue(accepts(ConfigCompletionPatterns.tagsArrayValue(), sqlx("config {\n  tags: [\"" + D + "\"]\n}")));
    }

    public void testTagsArrayValueRejectsTagsOutsideAnArray() {
        assertFalse(accepts(ConfigCompletionPatterns.tagsArrayValue(), sqlx("config {\n  tags: " + D + "\n}")));
    }

    public void testTagsArrayValueRejectsAnotherProperty() {
        assertFalse(accepts(ConfigCompletionPatterns.tagsArrayValue(), sqlx("config {\n  description: \"" + D + "\"\n}")));
    }

    public void testColumnSlotStoresTheNameValueSlot() {
        assertTrue(accepts(ConfigCompletionPatterns.columnSlot(),
                sqlx("config {\n  bigquery: { clusterBy: [\"" + D + "\"] }\n}")));
        assertEquals(ConfigColumnSlots.Kind.NAME_VALUE, context.get(ConfigCompletionPatterns.COLUMN_SLOT).kind());
    }

    public void testColumnSlotStoresTheEntryKeySlot() {
        assertTrue(accepts(ConfigCompletionPatterns.columnSlot(), sqlx("config {\n  columns: {\n    " + D + "\n  }\n}")));
        assertEquals(ConfigColumnSlots.Kind.ENTRY_KEY, context.get(ConfigCompletionPatterns.COLUMN_SLOT).kind());
    }

    public void testColumnSlotRejectsANonColumnString() {
        assertFalse(accepts(ConfigCompletionPatterns.columnSlot(), sqlx("config {\n  description: \"" + D + "\"\n}")));
        assertNull(context.get(ConfigCompletionPatterns.COLUMN_SLOT));
    }

    public void testKeyPositionAcceptsAShorthandKeyBeingTyped() {
        assertTrue(accepts(ConfigCompletionPatterns.keyPosition(), sqlx("config {\n  type: \"table\",\n  " + D + "\n}")));
    }

    public void testKeyPositionRejectsAValue() {
        assertFalse(accepts(ConfigCompletionPatterns.keyPosition(), sqlx("config {\n  type: \"" + D + "\"\n}")));
    }

    private boolean accepts(ElementPattern<? extends PsiElement> pattern, PsiElement leaf) {
        return pattern.accepts(leaf, context);
    }

    private PsiElement sqlx(String blocks) {
        PsiFile host = myFixture.configureByText("mart.sqlx", blocks + "\nSELECT 1\n");
        int offset = host.getText().indexOf(D);
        PsiElement leaf = InjectedLanguageManager.getInstance(getProject()).findInjectedElementAt(host, offset);
        assertNotNull("no injected leaf in\n" + host.getText(), leaf);
        return leaf;
    }
}
