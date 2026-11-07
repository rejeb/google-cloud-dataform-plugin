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
package io.github.rejeb.dataform.language.ui;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.InlineBanner;

import javax.swing.JEditorPane;
import java.awt.Component;
import java.awt.Container;

public class BannersTest extends BasePlatformTestCase {

    public void testErrorBannerHasErrorStatus() {
        InlineBanner banner = Banners.error("Table not found");

        assertEquals(EditorNotificationPanel.Status.Error, banner.getStatus());
        assertTrue(banner.getToolTipText().contains("Table not found"));
    }

    public void testInfoAndWarningStatuses() {
        assertEquals(EditorNotificationPanel.Status.Info, Banners.info("i").getStatus());
        assertEquals(EditorNotificationPanel.Status.Warning, Banners.warning("w").getStatus());
    }

    public void testALongMessageKeepsItsFullTextInTheTooltip() {
        String message = "x".repeat(1980) + "END-OF-MESSAGE-TAIL1";

        InlineBanner banner = Banners.error(message);

        assertTrue(banner.getToolTipText().contains("END-OF-MESSAGE-TAIL1"));
    }

    public void testMarkupInTheMessageIsEscaped() {
        InlineBanner banner = Banners.error("<b>Unrecognized</b> name");

        assertFalse(banner.getToolTipText().contains("<b>"));
    }

    public void testSetMessageReplacesTheTooltip() {
        InlineBanner banner = Banners.error("first");

        Banners.setMessage(banner, "second");

        assertTrue(banner.getToolTipText().contains("second"));
        assertFalse(banner.getToolTipText().contains("first"));
    }

    public void testSetMessageWithATitleKeepsTheTitleAboveTheMessage() {
        InlineBanner banner = Banners.error("first");

        Banners.setMessage(banner, "BigQuery dry run failed", "Not <found>");

        String text = messageOf(banner);
        assertTrue(text, text.contains("<b>BigQuery dry run failed</b>"));
        assertTrue(text, text.indexOf("BigQuery dry run failed") < text.indexOf("Not &lt;found&gt;"));
        assertTrue(banner.getToolTipText().contains("Not &lt;found&gt;"));
    }

    private static String messageOf(Container root) {
        for (Component child : root.getComponents()) {
            if (child instanceof JEditorPane pane) return pane.getText();
            if (child instanceof Container nested) {
                String text = messageOf(nested);
                if (text != null) return text;
            }
        }
        return null;
    }
}
