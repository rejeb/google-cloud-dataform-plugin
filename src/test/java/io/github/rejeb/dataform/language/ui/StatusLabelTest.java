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

import com.intellij.icons.AllIcons;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.AnimatedIcon;
import com.intellij.util.ui.UIUtil;

public class StatusLabelTest extends BasePlatformTestCase {

    public void testOkUsesAnIconNotAColour() {
        StatusLabel label = new StatusLabel();

        label.ok("Connected");

        assertEquals("Connected", label.getText());
        assertSame(AllIcons.General.InspectionsOK, label.getIcon());
        assertEquals(UIUtil.getLabelForeground(), label.getForeground());
        assertEquals(StatusLabel.Kind.OK, label.kind());
    }

    public void testErrorUsesAnIconNotAColour() {
        StatusLabel label = new StatusLabel();

        label.error("Repository not found");

        assertSame(AllIcons.General.Error, label.getIcon());
        assertEquals(UIUtil.getLabelForeground(), label.getForeground());
        assertEquals(StatusLabel.Kind.ERROR, label.kind());
    }

    public void testEveryKindHasItsIcon() {
        StatusLabel label = new StatusLabel();

        label.warning("w");
        assertSame(AllIcons.General.Warning, label.getIcon());
        label.info("i");
        assertSame(AllIcons.General.Information, label.getIcon());
        label.progress("p");
        assertSame(AnimatedIcon.Default.INSTANCE, label.getIcon());
        assertEquals(StatusLabel.Kind.PROGRESS, label.kind());
    }

    public void testClearRemovesTextAndIcon() {
        StatusLabel label = new StatusLabel();
        label.error("e");

        label.clear();

        assertEquals("", label.getText());
        assertNull(label.getIcon());
        assertEquals(StatusLabel.Kind.NONE, label.kind());
    }

    public void testAMultiLineMessageShowsItsFirstLineAndKeepsAllOfItInTheTooltip() {
        StatusLabel label = new StatusLabel();

        label.error("npm install failed\nnpm ERR! code E404\nnpm ERR! 404 Not Found");

        assertEquals("npm install failed", label.getText());
        assertTrue(label.getToolTipText().contains("npm ERR! 404 Not Found"));
    }

    public void testASingleLineMessageHasNoTooltip() {
        StatusLabel label = new StatusLabel();
        label.error("first\nsecond");

        label.ok("done");

        assertEquals("done", label.getText());
        assertNull(label.getToolTipText());
    }
}
