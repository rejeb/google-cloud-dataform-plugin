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

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import io.github.rejeb.dataform.language.ui.ReadOnlyTextFields;
import io.github.rejeb.dataform.language.gcp.pricing.CostText;

/**
 * Hovering anywhere on a cost row, the value or its note, explains the price.
 */
public class CostRowTooltipTest extends BasePlatformTestCase {

    public void testThePriceIsExplainedOnTheValueAndTheNote() {
        JBTextField value = ReadOnlyTextFields.singleLine("—");
        JBLabel note = new JBLabel();
        RunConfigUiUtils.costRow(value, note);

        RunConfigUiUtils.showCost(value, note, new CostText("$0.0295", "EU", true, "<html>why</html>"));

        assertEquals("<html>why</html>", value.getToolTipText());
        assertEquals("<html>why</html>", note.getToolTipText());
    }

    public void testACostWithoutPriceHasNoTooltip() {
        JBTextField value = ReadOnlyTextFields.singleLine("—");
        JBLabel note = new JBLabel();
        RunConfigUiUtils.showCost(value, note, new CostText("$0.0295", "EU", true, "<html>why</html>"));

        RunConfigUiUtils.showCost(value, note, new CostText("—", "", true));

        assertNull(value.getToolTipText());
        assertNull(note.getToolTipText());
    }
}
