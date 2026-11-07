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
package io.github.rejeb.dataform.language.gcp.toolwindow.action;

import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import io.github.rejeb.dataform.language.DataformIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Insets;

/**
 * The flat button of a Dataform tool window picker, as tall as the prototype toolbar row: its icon,
 * its name in bold two points above the IDE font and cut with an ellipsis past a maximum width,
 * then a chevron, six pixels apart, opening the choices of its action group.
 */
public final class PickerButton extends ActionButtonWithText {

    private static final String ELLIPSIS = "…";
    private static final int GAP = 6;
    private static final int PADDING = 8;
    private static final float FONT_INCREASE = 2f;
    static final int HEIGHT = 28;

    private final int maxLabelWidth;

    /**
     * A button for the picker {@code action}, whose name never takes more than
     * {@code maxLabelWidth} unscaled pixels when measured in the IDE font, so that it keeps as many
     * characters as the prototype while drawn bigger and bold.
     */
    public PickerButton(@NotNull AnAction action, @NotNull Presentation presentation, @NotNull String place,
                        int maxLabelWidth) {
        super(action, presentation, place, () -> ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE);
        this.maxLabelWidth = maxLabelWidth;
        setFont(nameFont());
    }

    @Override
    public void updateUI() {
        super.updateUI();
        setFont(nameFont());
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        size.height = Math.max(size.height, JBUI.scale(HEIGHT));
        return size;
    }

    @Override
    protected @NotNull Icon getDownArrowIcon() {
        return DataformIcons.CHEVRON_DOWN;
    }

    @Override
    protected int iconTextSpace() {
        return JBUI.scale(GAP);
    }

    @Override
    protected @NotNull Insets getMargins() {
        return JBUI.insets(0, PADDING);
    }

    private static @NotNull Font nameFont() {
        return JBFont.label().biggerOn(FONT_INCREASE).asBold();
    }

    @Override
    protected @NotNull String getText() {
        String text = super.getText();
        FontMetrics metrics = getFontMetrics(JBFont.label());
        int maxWidth = JBUI.scale(maxLabelWidth);
        if (metrics.stringWidth(text) <= maxWidth) {
            return text;
        }
        int end = text.length();
        while (end > 0 && metrics.stringWidth(text.substring(0, end).stripTrailing() + ELLIPSIS) > maxWidth) {
            end--;
        }
        return text.substring(0, end).stripTrailing() + ELLIPSIS;
    }
}
