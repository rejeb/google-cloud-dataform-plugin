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
package io.github.rejeb.dataform.language.diagnostics;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import javax.swing.JComponent;
import java.util.List;
import java.util.function.Function;

public class UnlocatedErrorsNotificationProviderTest extends BasePlatformTestCase {

    public void testNoBannerWhenThereAreNoDiagnostics() {
        PsiFile file = myFixture.configureByText("a.sqlx", "select 1\n");
        Function<? super FileEditor, ? extends JComponent> data =
                new UnlocatedErrorsNotificationProvider()
                        .collectNotificationData(getProject(), file.getVirtualFile());
        assertNull(data);
    }

    public void testNoBannerForFileOutsideDataformProject() {
        PsiFile file = myFixture.configureByText("notes.txt", "hello\n");
        assertNull(new UnlocatedErrorsNotificationProvider()
                .collectNotificationData(getProject(), file.getVirtualFile()));
    }

    public void testBannerShowsTheActualErrorMessage() {
        String text = UnlocatedErrorsNotificationProvider.bannerText(
                List.of("Could not resolve \"stg_users\""));
        assertEquals("Dataform: Could not resolve \"stg_users\"", text);
        assertFalse(text, text.contains("Build window"));
    }

    public void testLongMessageIsNotHardWrapped() {
        String message = "Could not resolve the referenced table because the declaration is "
                + "missing from the project configuration file and no matching action exists "
                + "anywhere in the compiled graph for this workspace";
        String text = UnlocatedErrorsNotificationProvider.bannerText(List.of(message));

        assertFalse("wrapping must be left to the banner component", text.contains("\n"));
        assertTrue(text, text.endsWith("workspace"));
    }

    public void testMultiLineMessageIsCollapsedToOneLogicalLine() {
        String text = UnlocatedErrorsNotificationProvider.bannerText(
                List.of("first line\n    second line"));
        assertEquals("Dataform: first line second line", text);
    }

    public void testSeveralErrorsAreBulletedOnSeparateLines() {
        String text = UnlocatedErrorsNotificationProvider.bannerText(
                List.of("first problem", "second problem"));
        assertEquals("Dataform:\n• first problem\n• second problem", text);
    }

    public void testMarkupInAMessageIsLeftAsPlainText() {
        String text = UnlocatedErrorsNotificationProvider.bannerText(
                List.of("bad <tag> & ampersand"));
        assertTrue(text, text.contains("<tag>"));
        assertTrue(text, text.contains("&"));
    }
}
