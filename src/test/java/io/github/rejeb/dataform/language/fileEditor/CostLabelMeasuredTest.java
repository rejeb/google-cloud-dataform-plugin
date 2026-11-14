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

import com.intellij.icons.AllIcons;
import com.intellij.util.ui.UIUtil;
import io.github.rejeb.dataform.language.gcp.pricing.CostText;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class CostLabelMeasuredTest {

    private static final CostText COST = new CostText("$0.26", "EU · $6.25 per TiB billed · list price", true, "tooltip");

    private final CostLabel label = new CostLabel();

    @Test
    void aMeasuredCostShowsLikeAnEstimateWithoutWarning() {
        label.showMeasured("42.35 GB", COST, false);

        assertEquals("42.35 GB · $0.26", label.getText());
        assertNull(label.getIcon());
        assertEquals(UIUtil.getLabelForeground(), label.getForeground());
        assertEquals("tooltip", label.getToolTipText());
    }

    @Test
    void anOutdatedMeasuredCostKeepsItsFiguresBehindAWarning() {
        label.showMeasured("42.35 GB", COST, true);

        assertEquals("42.35 GB · $0.26", label.getText());
        assertSame(AllIcons.General.Warning, label.getIcon());
        assertEquals(CostLabel.WARNING_FOREGROUND, label.getForeground());
    }

    @Test
    void aMeasuredCostAtAnUnreliablePriceIsAWarning() {
        label.showMeasured("42.35 GB", new CostText("$0.26", "note", false, "tooltip"), false);

        assertSame(AllIcons.General.Warning, label.getIcon());
    }

    @Test
    void aQueryToRunSaysSoAsAWarning() {
        label.showRunRequired();

        assertEquals("Run to estimate", label.getText());
        assertSame(AllIcons.General.Warning, label.getIcon());
        assertEquals(CostLabel.WARNING_FOREGROUND, label.getForeground());
    }
}
