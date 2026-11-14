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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.AnimatedIcon;
import com.intellij.ui.ClientProperty;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.SwingTextTrimmer;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import javax.swing.ToolTipManager;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.MouseEvent;

/**
 * A label telling the outcome of an operation with a status icon next to default-coloured text,
 * such as a connection test or an installation. A message of several lines shows its first line and
 * keeps the whole message in the tooltip. Trimmed to its given width, the label never widens its
 * layout, whatever the length of its text.
 */
public class StatusLabel extends JBLabel {

    /**
     * What the label currently reports.
     */
    public enum Kind { NONE, OK, ERROR, WARNING, INFO, PROGRESS }

    private Kind kind = Kind.NONE;
    private @Nullable SwingTextTrimmer trimmer;

    public StatusLabel() {
        super("");
    }

    /**
     * Reports a success.
     */
    public void ok(@NotNull String text) {
        show(Kind.OK, AllIcons.General.InspectionsOK, text);
    }

    /**
     * Reports a failure.
     */
    public void error(@NotNull String text) {
        show(Kind.ERROR, AllIcons.General.Error, text);
    }

    /**
     * Reports a problem that does not stop the user.
     */
    public void warning(@NotNull String text) {
        show(Kind.WARNING, AllIcons.General.Warning, text);
    }

    /**
     * Reports a neutral fact.
     */
    public void info(@NotNull String text) {
        show(Kind.INFO, AllIcons.General.Information, text);
    }

    /**
     * Reports an operation in progress with a spinning icon.
     */
    public void progress(@NotNull String text) {
        show(Kind.PROGRESS, AnimatedIcon.Default.INSTANCE, text);
    }

    /**
     * Removes the text and the icon.
     */
    public void clear() {
        show(Kind.NONE, null, "");
    }

    /**
     * What the label currently reports.
     */
    public @NotNull Kind kind() {
        return kind;
    }

    /**
     * Makes the label take the width its layout gives it, such as the column of a form, instead of
     * the width of its text: it asks only for the width of its icon, a text too long for the given
     * width is shortened by the trimmer, and the tooltip then shows the whole text.
     */
    public void trimToGivenWidth(@NotNull SwingTextTrimmer trimmer) {
        this.trimmer = trimmer;
        ClientProperty.put(this, SwingTextTrimmer.KEY, trimmer);
        ToolTipManager.sharedInstance().registerComponent(this);
        revalidate();
        repaint();
    }

    /**
     * The tooltip set for a message of several lines when there is one; otherwise, once the label
     * is trimmed to its given width, the whole text while it is shown shortened.
     */
    @Override
    public @Nullable String getToolTipText(@NotNull MouseEvent event) {
        String explicit = super.getToolTipText(event);
        if (explicit != null || !isTrimmed()) {
            return explicit;
        }
        return getText();
    }

    @Override
    public Dimension getPreferredSize() {
        return givenWidth(super.getPreferredSize());
    }

    @Override
    public Dimension getMinimumSize() {
        return givenWidth(super.getMinimumSize());
    }

    private @NotNull Dimension givenWidth(@NotNull Dimension size) {
        if (trimmer == null || isPreferredSizeSet()) {
            return size;
        }
        Insets insets = getInsets();
        Icon icon = getIcon();
        int iconWidth = icon != null ? icon.getIconWidth() + getIconTextGap() : 0;
        size.width = Math.min(size.width, insets.left + iconWidth + insets.right);
        return size;
    }

    private boolean isTrimmed() {
        return trimmer != null && getWidth() < super.getPreferredSize().width;
    }

    private void show(@NotNull Kind kind, @Nullable Icon icon, @NotNull String text) {
        this.kind = kind;
        setIcon(icon);
        String[] lines = StringUtil.splitByLines(text);
        setText(lines.length > 1 ? lines[0].strip() : text);
        setToolTipText(lines.length > 1 ? UiText.htmlTooltip(text) : null);
        if (trimmer != null) {
            ToolTipManager.sharedInstance().registerComponent(this);
        }
        setForeground(UIUtil.getLabelForeground());
        revalidate();
        repaint();
    }
}
