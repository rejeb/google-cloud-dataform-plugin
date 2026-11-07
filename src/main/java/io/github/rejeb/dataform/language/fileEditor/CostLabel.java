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
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.UIUtil;
import io.github.rejeb.dataform.language.ui.UiText;
import io.github.rejeb.dataform.language.gcp.pricing.CostText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.awt.Color;

/**
 * The estimated cost of an action, at the right of its section header in the compiled query preview.
 */
final class CostLabel extends JBLabel {

    static final Color WARNING_FOREGROUND = JBColor.namedColor("Label.warningForeground", JBColor.ORANGE);
    private static final String ESTIMATING = "Estimating…";
    private static final String SIGNED_OUT = "Sign in to estimate";
    private static final String SIGNED_OUT_TOOLTIP = "Sign in with the banner at the top of the editor.";
    private static final String FAILED = "Can't estimate";
    private static final String NOT_COMPILED = "Not compiled";

    CostLabel() {
        super("");
        setFont(JBFont.medium());
    }

    void showEstimating() {
        show(ESTIMATING, AnimatedIcon.Default.INSTANCE, null, UIUtil.getContextHelpForeground());
    }

    void showSignedOut() {
        show(SIGNED_OUT, null, SIGNED_OUT_TOOLTIP, UIUtil.getContextHelpForeground());
    }

    void showNotCompiled() {
        show(NOT_COMPILED, null, null, UIUtil.getErrorForeground());
    }

    void showFailure(@NotNull String message) {
        show(FAILED, null, message, UIUtil.getErrorForeground());
    }

    void showCost(@NotNull String bytes, @NotNull CostText cost) {
        String text = UiText.dot(bytes, cost.value());
        if (cost.tooltip().isEmpty()) {
            show(text, null, null, UIUtil.getLabelForeground());
            return;
        }
        show(text,
                cost.reliable() ? null : AllIcons.General.Warning,
                cost.tooltip(),
                cost.reliable() ? UIUtil.getLabelForeground() : WARNING_FOREGROUND);
    }

    void clear() {
        show("", null, null, UIUtil.getLabelForeground());
    }

    private void show(@NotNull String text, @Nullable Icon icon, @Nullable String tooltip, @NotNull Color foreground) {
        setText(text);
        setIcon(icon);
        setToolTipText(tooltip);
        setForeground(foreground);
        revalidate();
        repaint();
    }
}
