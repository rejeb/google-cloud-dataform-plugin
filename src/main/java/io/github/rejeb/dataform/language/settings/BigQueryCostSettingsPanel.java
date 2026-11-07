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
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.panel.ComponentPanelBuilder;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import io.github.rejeb.dataform.language.ui.StatusLabel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The "BigQuery cost" section of the Dataform Tools settings page: whether the compiled query
 * preview shows an estimated cost, how bytes are priced and the currency costs are shown in.
 */
public final class BigQueryCostSettingsPanel {

    private static final Pattern CURRENCY_CODE = Pattern.compile("[A-Z]{3}");
    private static final String[] CURRENCIES = {"USD", "EUR", "GBP", "JPY"};
    private static final int INDENT = 20;
    private static final int RADIO_TEXT_OFFSET = 24;

    private final JBCheckBox showEstimatedCostBox =
            new JBCheckBox("Show estimated cost in the compiled query preview");
    private final JBRadioButton listPriceRadio =
            new JBRadioButton("List price from Google Cloud Billing, per query location");
    private final JBRadioButton fixedPriceRadio = new JBRadioButton("Fixed price");
    private final JBRadioButton capacityRadio = new JBRadioButton("Capacity pricing (reservations)");
    private final JBTextField fixedPriceField = new JBTextField(6);
    private final ComboBox<String> currencyCombo = new ComboBox<>(CURRENCIES);
    private final StatusLabel priceListStatusLabel = new StatusLabel();
    private final ActionLink refreshLink = new ActionLink("Refresh");
    private final JPanel component;

    /**
     * Builds the section with every control in its default state.
     */
    public BigQueryCostSettingsPanel() {
        ButtonGroup modes = new ButtonGroup();
        modes.add(listPriceRadio);
        modes.add(fixedPriceRadio);
        modes.add(capacityRadio);
        listPriceRadio.setSelected(true);
        currencyCombo.setEditable(true);
        listPriceRadio.addItemListener(e -> updateFixedPriceField());
        fixedPriceRadio.addItemListener(e -> updateFixedPriceField());
        capacityRadio.addItemListener(e -> updateFixedPriceField());
        updateFixedPriceField();

        JPanel fixedPriceRow = indented(row(fixedPriceRadio, fixedPriceField, new JBLabel("per TiB, every location")));
        JPanel currencyRow = row(new JBLabel("Currency:"), currencyCombo);
        JPanel statusRow = row(priceListStatusLabel, refreshLink);

        component = FormBuilder.createFormBuilder()
                .addComponent(new TitledSeparator("BigQuery cost"))
                .addComponent(showEstimatedCostBox)
                .addComponent(new JBLabel("Price billed bytes with:"))
                .addComponent(indented(listPriceRadio))
                .addComponent(hint("Excludes the 1 TiB monthly free tier and contract discounts."))
                .addComponent(fixedPriceRow)
                .addComponent(indented(capacityRadio))
                .addComponent(hint("Bytes only, no price. Jobs that report a reservation are always shown this way."))
                .addComponent(currencyRow)
                .addComponent(statusRow)
                .getPanel();
    }

    /**
     * Returns the Swing component of the section.
     */
    public @NotNull JComponent getComponent() {
        return component;
    }

    /**
     * Shows the stored cost settings in the controls.
     */
    public void reset(@NotNull DataformToolsSettings settings) {
        showEstimatedCostBox.setSelected(settings.isShowEstimatedCost());
        radioFor(settings.getPriceMode()).setSelected(true);
        fixedPriceField.setText(settings.getFixedPricePerTib());
        currencyCombo.setSelectedItem(settings.getCurrency());
        currencyCombo.getEditor().setItem(settings.getCurrency());
        updateFixedPriceField();
    }

    /**
     * Tells whether the controls differ from the stored cost settings.
     */
    public boolean isModified(@NotNull DataformToolsSettings settings) {
        return showEstimatedCostBox.isSelected() != settings.isShowEstimatedCost()
                || selectedMode() != settings.getPriceMode()
                || !fixedPriceText().equals(settings.getFixedPricePerTib())
                || !currencyText().equals(settings.getCurrency());
    }

    /**
     * Validates the controls and stores them; nothing is stored when a value is rejected.
     */
    public void apply(@NotNull DataformToolsSettings settings) throws ConfigurationException {
        BigQueryPriceMode mode = selectedMode();
        String fixedPrice = fixedPriceText();
        if (mode == BigQueryPriceMode.FIXED_PRICE && !isPositiveNumber(fixedPrice)) {
            throw new ConfigurationException("Fixed price per TiB must be a positive number");
        }
        String currency = currencyText();
        if (!CURRENCY_CODE.matcher(currency).matches()) {
            throw new ConfigurationException("Currency must be a three-letter ISO 4217 code");
        }
        settings.updateCostSettings(showEstimatedCostBox.isSelected(), mode, fixedPrice, currency);
    }

    /**
     * Shows where the price list comes from and when it was last downloaded.
     */
    public void setPriceListStatus(@NotNull String text) {
        priceListStatusLabel.info(text);
    }

    /**
     * Runs the given action when the Refresh link is clicked.
     */
    public void onRefresh(@NotNull Runnable action) {
        refreshLink.addActionListener(e -> action.run());
    }

    private @NotNull BigQueryPriceMode selectedMode() {
        if (fixedPriceRadio.isSelected()) {
            return BigQueryPriceMode.FIXED_PRICE;
        }
        if (capacityRadio.isSelected()) {
            return BigQueryPriceMode.CAPACITY;
        }
        return BigQueryPriceMode.LIST_PRICE;
    }

    private @NotNull JBRadioButton radioFor(@NotNull BigQueryPriceMode mode) {
        return switch (mode) {
            case LIST_PRICE -> listPriceRadio;
            case FIXED_PRICE -> fixedPriceRadio;
            case CAPACITY -> capacityRadio;
        };
    }

    private @NotNull String fixedPriceText() {
        return fixedPriceField.getText().trim();
    }

    private @NotNull String currencyText() {
        Object item = currencyCombo.getEditor().getItem();
        return item == null ? "" : item.toString().trim().toUpperCase(Locale.ROOT);
    }

    private void updateFixedPriceField() {
        fixedPriceField.setEnabled(fixedPriceRadio.isSelected());
    }

    private static boolean isPositiveNumber(@NotNull String text) {
        try {
            return new BigDecimal(text).signum() > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static @NotNull JComponent hint(@NotNull String text) {
        JComponent comment = ComponentPanelBuilder.createCommentComponent(text, true);
        comment.setBorder(JBUI.Borders.emptyLeft(INDENT + RADIO_TEXT_OFFSET));
        return comment;
    }

    private static @NotNull JPanel indented(@NotNull JComponent component) {
        JPanel panel = component instanceof JPanel row ? row : wrap(component);
        panel.setBorder(JBUI.Borders.emptyLeft(INDENT));
        return panel;
    }

    private static @NotNull JPanel wrap(@NotNull JComponent component) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(component, BorderLayout.WEST);
        return panel;
    }

    private static @NotNull JPanel row(@NotNull JComponent... components) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        for (int i = 0; i < components.length; i++) {
            if (i > 0) {
                row.add(Box.createHorizontalStrut(JBUI.scale(6)));
            }
            row.add(components[i]);
        }
        return row;
    }
}
