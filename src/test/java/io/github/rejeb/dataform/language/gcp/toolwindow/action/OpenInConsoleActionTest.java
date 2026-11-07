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
package io.github.rejeb.dataform.language.gcp.toolwindow.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.DataformIcons;
import io.github.rejeb.dataform.language.gcp.common.DataformConsoleUrls;
import io.github.rejeb.dataform.language.gcp.settings.DataformRepositoryConfig;

public class OpenInConsoleActionTest extends BasePlatformTestCase {

    private static final DataformRepositoryConfig SALES =
            new DataformRepositoryConfig("sales-id", "Sales", "my-gcp-project", "sales", "europe-west1");

    public void testItIsAnExternalLink() {
        OpenInConsoleAction action = new OpenInConsoleAction(() -> SALES, () -> null);

        assertEquals("Open in Google Cloud Console", action.getTemplatePresentation().getText());
        assertSame(DataformIcons.EXTERNAL_LINK, action.getTemplatePresentation().getIcon());
    }

    public void testWithAWorkspaceItOpensTheWorkspace() {
        OpenInConsoleAction action = new OpenInConsoleAction(() -> SALES, () -> "dev");

        Presentation presentation = updated(action);

        assertTrue(presentation.isEnabled());
        assertEquals("Open workspace dev in the Dataform console", presentation.getDescription());
        assertEquals(DataformConsoleUrls.of(SALES, "dev"), action.url());
    }

    public void testOnTheMainBranchItOpensTheRepository() {
        OpenInConsoleAction action = new OpenInConsoleAction(() -> SALES, () -> null);

        Presentation presentation = updated(action);

        assertTrue(presentation.isEnabled());
        assertEquals("Open repository Sales in the Dataform console", presentation.getDescription());
        assertEquals(DataformConsoleUrls.of(SALES, null), action.url());
    }

    public void testOnAFileOfAWorkspaceItOpensTheFile() {
        OpenInConsoleAction action = new OpenInConsoleAction(() -> SALES, () -> "dev", () -> "definitions/a.sqlx");

        Presentation presentation = updated(action);

        assertTrue(presentation.isEnabled());
        assertEquals("Open definitions/a.sqlx in the Dataform console", presentation.getDescription());
        assertEquals(DataformConsoleUrls.ofFile(SALES, "dev", "definitions/a.sqlx"), action.url());
    }

    public void testOnAFileOfTheMainBranchItOpensTheRepository() {
        OpenInConsoleAction action = new OpenInConsoleAction(() -> SALES, () -> null, () -> "definitions/a.sqlx");

        Presentation presentation = updated(action);

        assertEquals("Open repository Sales in the Dataform console", presentation.getDescription());
        assertEquals(DataformConsoleUrls.of(SALES, null), action.url());
    }

    public void testWithoutRepositoryItIsDisabled() {
        OpenInConsoleAction action = new OpenInConsoleAction(() -> null, () -> null);

        assertFalse(updated(action).isEnabled());
        assertNull(action.url());
    }

    private static Presentation updated(OpenInConsoleAction action) {
        AnActionEvent event = TestActionEvent.createTestEvent(action);
        action.update(event);
        return event.getPresentation();
    }
}
