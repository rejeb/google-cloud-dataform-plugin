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
package io.github.rejeb.dataform.language.fileEditor;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.ContextHelpLabel;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.UIUtil;
import io.github.rejeb.dataform.language.gcp.pricing.CostText;

import javax.swing.JLabel;
import java.awt.Component;
import java.util.concurrent.atomic.AtomicBoolean;

public class QueryCostTotalBarTest extends BasePlatformTestCase {

    public void testTheTotalIsATransparentRowWithAGrayedTitle() {
        QueryCostTotalBar bar = new QueryCostTotalBar(() -> {
        });

        bar.update(new PreviewCostTotal(1024, 2, 2), "1 KB", new CostText("≈ $0.01", "EU", true, "Dry-run estimate"));

        assertTrue(bar.isVisible());
        assertFalse(bar.isOpaque());
        assertEquals(UIUtil.getContextHelpForeground(), titleLabel(bar).getForeground());
    }

    public void testAPriceTooltipShowsAContextHelpIcon() {
        QueryCostTotalBar bar = new QueryCostTotalBar(() -> {
        });

        bar.update(new PreviewCostTotal(1024, 2, 2), "1 KB", new CostText("≈ $0.01", "EU", true, "Dry-run estimate"));

        ContextHelpLabel help = helpLabel(bar);
        assertNotNull(help);
        assertTrue(help.isVisible());
    }

    public void testNoTooltipHidesTheContextHelpIcon() {
        QueryCostTotalBar bar = new QueryCostTotalBar(() -> {
        });

        bar.update(new PreviewCostTotal(1024, 2, 2), "1 KB", new CostText("capacity pricing", "", true));

        ContextHelpLabel help = helpLabel(bar);
        assertTrue(help == null || !help.isVisible());
    }

    public void testTheBytesAndTheCostAreBoldAndOnePointSmaller() {
        QueryCostTotalBar bar = new QueryCostTotalBar(() -> {
        });

        bar.update(new PreviewCostTotal(1024, 2, 2), "1 KB", new CostText("≈ $0.01", "EU", true, "Dry-run estimate"));

        assertEquals("1 KB · ≈ $0.01", bar.valueText());
        JLabel bytes = labelWithText(bar, "1 KB");
        assertEquals(JBFont.medium().asBold(), bytes.getFont());
        assertEquals(JBFont.medium().asBold(), labelWithText(bar, "≈ $0.01").getFont());
        assertEquals(JBFont.medium(), titleLabel(bar).getFont());
    }

    public void testTheSignInLinkSignsIn() {
        AtomicBoolean signedIn = new AtomicBoolean();
        QueryCostTotalBar bar = new QueryCostTotalBar(() -> signedIn.set(true));

        bar.showSignedOut();
        bar.signInLink().doClick();

        assertTrue(bar.isVisible());
        assertTrue(signedIn.get());
    }

    private static JLabel labelWithText(QueryCostTotalBar bar, String text) {
        for (Component child : bar.getComponents()) {
            if (child instanceof JLabel label && text.equals(label.getText())) return label;
        }
        throw new AssertionError("no label " + text);
    }

    private static JLabel titleLabel(QueryCostTotalBar bar) {
        for (Component child : bar.getComponents()) {
            if (child instanceof JLabel label && bar.titleText().equals(label.getText())) return label;
        }
        throw new AssertionError("no title label");
    }

    private static ContextHelpLabel helpLabel(QueryCostTotalBar bar) {
        for (Component child : bar.getComponents()) {
            if (child instanceof ContextHelpLabel help) return help;
        }
        return null;
    }
}
