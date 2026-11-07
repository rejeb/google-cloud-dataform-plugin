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
package io.github.rejeb.dataform.language.gcp.execution.workflow.runconfig.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.components.BrowserLink;
import com.intellij.util.ui.JBFont;

public class IdLinksTest extends BasePlatformTestCase {

    public void testAnIdLinkOpensItsUrlAndShowsItsTip() {
        BrowserLink link = IdLinks.create("job1", "https://console.cloud.google.com/bigquery", "Open the job in the BigQuery console");

        assertEquals("job1", link.getText());
        assertEquals("https://console.cloud.google.com/bigquery", link.getUrl());
        assertEquals("Open the job in the BigQuery console", link.getToolTipText());
        assertIdStyle(link);
    }

    /**
     * Asserts that a link has the id style of the run console: the editor font at the small size of the
     * prototype, with the external-link arrow.
     */
    static void assertIdStyle(BrowserLink link) {
        assertSame(link.getText(), AllIcons.Ide.External_link_arrow, link.getIcon());
        assertEquals(link.getText(), EditorColorsManager.getInstance().getGlobalScheme().getFont(EditorFontType.PLAIN).getFamily(),
                link.getFont().getFamily());
        assertEquals(link.getText(), JBFont.medium().getSize2D(), link.getFont().getSize2D(), 0.01f);
    }
}
