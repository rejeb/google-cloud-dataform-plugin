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
import com.intellij.ui.AnimatedIcon;
import com.intellij.ui.ContextHelpLabel;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.panels.HorizontalLayout;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.UIUtil;
import io.github.rejeb.dataform.language.gcp.pricing.CostText;
import io.github.rejeb.dataform.language.ui.UiText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.event.ActionListener;
import java.util.List;

/**
 * The estimated cost of all the actions of a file, at the right of the query preview header:
 * grayed "Estimated", then the bytes and the cost in bold. While the first estimate runs it says so,
 * and signed out it offers to sign in.
 */
final class QueryCostTotalBar extends JPanel {

    private static final String ESTIMATED = "Estimated";
    private static final String ESTIMATING = "Estimating…";
    private static final String SIGN_IN = "Sign in to estimate the cost";
    private static final String DOT = "·";

    private final JBLabel title = new JBLabel();
    private final JBLabel bytes = new JBLabel();
    private final JBLabel dot = new JBLabel(DOT);
    private final JBLabel cost = new JBLabel();
    private final ActionLink signIn;
    private @Nullable ContextHelpLabel help;

    /**
     * @param signIn starts the Google Cloud sign-in, from the link shown when signed out
     */
    QueryCostTotalBar(@NotNull Runnable signIn) {
        super(new HorizontalLayout(6, SwingConstants.CENTER));
        setOpaque(false);
        this.signIn = new ActionLink(SIGN_IN, (ActionListener) e -> signIn.run());
        this.signIn.setFont(JBFont.medium());
        title.setFont(JBFont.medium());
        title.setForeground(UIUtil.getContextHelpForeground());
        dot.setFont(JBFont.medium());
        dot.setForeground(UIUtil.getContextHelpForeground());
        bytes.setFont(JBFont.medium().asBold());
        cost.setFont(JBFont.medium().asBold());
        add(title);
        add(bytes);
        add(dot);
        add(cost);
        add(this.signIn);
        setVisible(false);
    }

    void update(@NotNull PreviewCostTotal total, @NotNull String bytesText, @NotNull CostText costText) {
        if (!total.visible()) {
            hideTotal();
            return;
        }
        String progress = total.estimated() < total.actions()
                ? " (" + total.estimated() + " of " + total.actions() + ")"
                : "";
        showOnly(title, bytes, dot, cost);
        title.setText(ESTIMATED + progress);
        title.setIcon(null);
        bytes.setText(bytesText);
        cost.setText(costText.value());
        cost.setIcon(costText.reliable() ? null : AllIcons.General.Warning);
        cost.setForeground(costText.reliable() ? UIUtil.getLabelForeground() : CostLabel.WARNING_FOREGROUND);
        String tooltip = costText.tooltip().isEmpty() ? null : costText.tooltip();
        title.setToolTipText(tooltip);
        bytes.setToolTipText(tooltip);
        cost.setToolTipText(tooltip);
        replaceHelp(tooltip == null ? null : ContextHelpLabel.create(tooltip));
        showRow();
    }

    void showEstimating() {
        showOnly(title);
        title.setText(ESTIMATING);
        title.setIcon(AnimatedIcon.Default.INSTANCE);
        title.setToolTipText(null);
        replaceHelp(null);
        showRow();
    }

    void showSignedOut() {
        showOnly(signIn);
        replaceHelp(null);
        showRow();
    }

    void hideTotal() {
        setVisible(false);
    }

    @NotNull
    String titleText() {
        return title.isVisible() ? title.getText() : "";
    }

    @NotNull
    String valueText() {
        return bytes.isVisible() ? UiText.dot(bytes.getText(), cost.getText()) : "";
    }

    @Nullable
    String tooltipText() {
        return cost.getToolTipText();
    }

    @NotNull
    ActionLink signInLink() {
        return signIn;
    }

    private void showOnly(@NotNull JComponent... shown) {
        List<JComponent> visible = List.of(shown);
        for (JComponent component : List.of(title, bytes, dot, cost, signIn)) {
            component.setVisible(visible.contains(component));
        }
    }

    private void replaceHelp(@Nullable ContextHelpLabel replacement) {
        if (help != null) remove(help);
        help = replacement;
        if (help != null) add(help);
    }

    private void showRow() {
        setVisible(true);
        revalidate();
        repaint();
    }
}
