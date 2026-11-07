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
package io.github.rejeb.dataform.language.completion;

import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.openapi.editor.Editor;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.LoggedErrorProcessor;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.testFramework.fixtures.CompletionAutoPopupTester;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The caret after a template chosen from the popup that opens on its own when {@code $} is typed.
 */
public class SqlxTemplateAutoPopupTest extends BasePlatformTestCase {

    private static final LoggedErrorProcessor IGNORING_SQL_LOOKUP_LEAK = new LoggedErrorProcessor() {
        @Override
        public @NotNull Set<Action> processError(@NotNull String category,
                                                 @NotNull String message,
                                                 String @NotNull [] details,
                                                 @Nullable Throwable throwable) {
            return message.contains("is retaining PSI")
                    ? EnumSet.noneOf(Action.class)
                    : EnumSet.allOf(Action.class);
        }
    };

    @Override
    protected boolean runInDispatchThread() {
        return false;
    }

    private int[] caretAfterChoosing(String text, String template, char finish) {
        int[] result = new int[2];
        String[] host = new String[1];
        try {
            LoggedErrorProcessor.executeWith(IGNORING_SQL_LOOKUP_LEAK, () -> {
                CompletionAutoPopupTester tester = new CompletionAutoPopupTester(myFixture);
                tester.runWithAutoPopupEnabled(() -> {
                    EdtTestUtil.runInEdtAndWait(() -> myFixture.configureByText("probe.sqlx", text));
                    tester.typeWithPauses("$");
                    Lookup lookup = tester.getLookup();
                    assertNotNull("typing $ must open the popup", lookup);
                    EdtTestUtil.runInEdtAndWait(() -> {
                        List<String> shown = myFixture.getLookupElementStrings();
                        LookupElement[] elements = myFixture.getLookupElements();
                        int index = shown.indexOf(template);
                        assertTrue("no " + template + " in " + shown, index >= 0);
                        myFixture.getLookup().setCurrentItem(elements[index]);
                        myFixture.finishLookup(finish);
                    });
                    tester.joinCompletion();
                    EdtTestUtil.runInEdtAndWait(() -> {
                        Editor editor = myFixture.getEditor() instanceof EditorWindow window
                                ? window.getDelegate() : myFixture.getEditor();
                        host[0] = editor.getDocument().getText();
                        result[0] = editor.getCaretModel().getOffset();
                    });
                });
            });
        } catch (Throwable e) {
            throw new AssertionError(e);
        }
        result[1] = host[0].indexOf(template);
        return result;
    }

    public void testRefCaretBetweenQuotesAfterEnter() {
        int[] caret = caretAfterChoosing("config { type: \"table\" }\n\nSELECT * FROM <caret>\nWHERE TRUE\n",
                "${ref(\"\")}", '\n');
        assertEquals(caret[1] + "${ref(\"".length(), caret[0]);
    }

    public void testRefCaretBetweenQuotesAfterTab() {
        int[] caret = caretAfterChoosing("config { type: \"table\" }\n\nSELECT * FROM <caret>\nWHERE TRUE\n",
                "${ref(\"\")}", '\t');
        assertEquals(caret[1] + "${ref(\"".length(), caret[0]);
    }

    public void testSelfCaretAtTheEndOfTheExpression() {
        int[] caret = caretAfterChoosing("config { type: \"table\" }\n\nSELECT * FROM <caret>\nWHERE TRUE\n",
                "${self()}", '\n');
        assertEquals(caret[1] + "${self()}".length(), caret[0]);
    }

    public void testRefCaretAtTheEndOfAnEarlierExpressionLine() {
        int[] caret = caretAfterChoosing(
                "config { type: \"table\" }\n\nSELECT * FROM ${ref(\"a\")}\nJOIN <caret>\nON TRUE\n",
                "${ref(\"\")}", '\n');
        assertEquals(caret[1] + "${ref(\"".length(), caret[0]);
    }
}
