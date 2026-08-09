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
package io.github.rejeb.dataform.language.validation;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.settings.DataformToolsSettings;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Problems reported against text the user is still typing are noise, so the annotator waits for
 * the quiet period tracked by {@link DataformEditActivityService}.
 */
public class DataformValidationAnnotatorTest extends BasePlatformTestCase {

    private static final String MESSAGE = "declared table is unknown";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        DataformToolsSettings.getInstance().setShowInlineCompilationErrors(true);
        ServiceContainerUtil.replaceService(getProject(), SqlxValidationService.class,
                new StubValidationService(), getTestRootDisposable());
    }

    private boolean isProblemPainted(boolean editing) {
        ServiceContainerUtil.replaceService(getProject(), DataformEditActivityService.class,
                new StubEditActivityService(editing), getTestRootDisposable());
        PsiFile file = myFixture.configureByText("a.sqlx", "config { type: \"table\" }\n\nselect 1\n");
        assertNotNull(file);
        List<HighlightInfo> infos = myFixture.doHighlighting();
        return infos.stream().anyMatch(info -> MESSAGE.equals(info.getDescription()));
    }

    public void testProblemsAreNotPaintedWhileTheUserIsTyping() {
        assertFalse("no syntax feedback is wanted mid-edit", isProblemPainted(true));
    }

    public void testProblemsArePaintedWhenTheUserIsNotTyping() {
        assertTrue("problems must appear once the user stops typing", isProblemPainted(false));
    }

    private record StubEditActivityService(boolean editing) implements DataformEditActivityService {

        @Override
        public void noteEdit() {
        }

        @Override
        public boolean isEditing() {
            return editing;
        }

        @Override
        public long remainingQuietPeriodMs() {
            return editing ? QUIET_PERIOD_MS : 0;
        }
    }

    private static final class StubValidationService implements SqlxValidationService {

        @Override
        public @NotNull List<SqlxValidationProblem> validate(@NotNull PsiFile file) {
            return List.of(new SqlxValidationProblem(TextRange.create(0, 6), MESSAGE,
                    SqlxValidationProblem.Kind.UNRESOLVED_REFERENCE));
        }
    }
}
