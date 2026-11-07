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
package io.github.rejeb.dataform.language.settings;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.JBRadioButton;
import io.github.rejeb.dataform.language.ui.StatusLabel;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The BigQuery cost settings keep the defaults of the spec, survive an old stored value and go
 * through the settings page unchanged.
 */
public class DataformToolsCostSettingsTest extends BasePlatformTestCase {

    public void testCostSettingsHaveTheSpecDefaults() {
        DataformToolsSettingsImpl settings = new DataformToolsSettingsImpl();

        assertTrue(settings.isShowEstimatedCost());
        assertEquals(BigQueryPriceMode.LIST_PRICE, settings.getPriceMode());
        assertEquals("6.25", settings.getFixedPricePerTib());
        assertEquals("USD", settings.getCurrency());
    }

    public void testAnUnknownStoredModeReadsAsListPrice() {
        DataformToolsSettingsImpl settings = new DataformToolsSettingsImpl();
        DataformToolsSettingsState state = new DataformToolsSettingsState();
        state.priceMode = "SOMETHING_OLD";
        settings.loadState(state);

        assertEquals(BigQueryPriceMode.LIST_PRICE, settings.getPriceMode());
    }

    public void testThePanelRoundTripsTheSettings() throws ConfigurationException {
        DataformToolsSettingsImpl settings = new DataformToolsSettingsImpl();
        settings.updateCostSettings(false, BigQueryPriceMode.FIXED_PRICE, "5.00", "EUR");
        BigQueryCostSettingsPanel panel = new BigQueryCostSettingsPanel();
        panel.reset(settings);

        assertFalse(panel.isModified(settings));

        DataformToolsSettingsImpl target = new DataformToolsSettingsImpl();
        panel.apply(target);
        assertFalse(target.isShowEstimatedCost());
        assertEquals(BigQueryPriceMode.FIXED_PRICE, target.getPriceMode());
        assertEquals("5.00", target.getFixedPricePerTib());
        assertEquals("EUR", target.getCurrency());
    }

    public void testAFixedPriceThatIsNotAPositiveNumberIsRejected() {
        DataformToolsSettingsImpl settings = new DataformToolsSettingsImpl();
        settings.updateCostSettings(true, BigQueryPriceMode.FIXED_PRICE, "abc", "USD");
        BigQueryCostSettingsPanel panel = new BigQueryCostSettingsPanel();
        panel.reset(settings);

        assertThrows(ConfigurationException.class, () -> panel.apply(new DataformToolsSettingsImpl()));
    }

    public void testACurrencyThatIsNotAnIsoCodeIsRejected() {
        DataformToolsSettingsImpl settings = new DataformToolsSettingsImpl();
        settings.updateCostSettings(true, BigQueryPriceMode.LIST_PRICE, "6.25", "euro");
        BigQueryCostSettingsPanel panel = new BigQueryCostSettingsPanel();
        panel.reset(settings);

        assertThrows(ConfigurationException.class, () -> panel.apply(new DataformToolsSettingsImpl()));
    }

    public void testRefreshIsALinkThatRunsTheAction() {
        BigQueryCostSettingsPanel panel = new BigQueryCostSettingsPanel();
        AtomicInteger runs = new AtomicInteger();
        panel.onRefresh(runs::incrementAndGet);

        ActionLink refresh = assertOneElement(DataformToolsSettingsPanelTest.all(panel.getComponent(), ActionLink.class));
        refresh.doClick();

        assertEquals("Refresh", refresh.getText());
        assertEquals(1, runs.get());
    }

    public void testThePriceListStatusIsAStatusLabel() {
        BigQueryCostSettingsPanel panel = new BigQueryCostSettingsPanel();

        panel.setPriceListStatus("Price list: downloaded 2026-11-01 07:00");

        StatusLabel status = assertOneElement(DataformToolsSettingsPanelTest.all(panel.getComponent(), StatusLabel.class));
        assertEquals("Price list: downloaded 2026-11-01 07:00", status.getText());
        assertEquals(StatusLabel.Kind.INFO, status.kind());
    }

    public void testThePriceModesAreIndented() {
        BigQueryCostSettingsPanel panel = new BigQueryCostSettingsPanel();

        for (JBRadioButton radio : DataformToolsSettingsPanelTest.all(panel.getComponent(), JBRadioButton.class)) {
            assertTrue(radio.getText(), radio.getParent().getInsets().left >= 20 || radio.getInsets().left >= 20);
        }
    }
}
