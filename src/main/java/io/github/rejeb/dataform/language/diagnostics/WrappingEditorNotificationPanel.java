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
package io.github.rejeb.dataform.language.diagnostics;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;
import javax.swing.JTextArea;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Insets;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * An editor banner whose message wraps to the editor width. When built with a line limit, the
 * banner never grows past that many lines: the message is cut with an ellipsis, and hovering the
 * banner shows it in full size over the editor.
 */
public class WrappingEditorNotificationPanel extends EditorNotificationPanel {

    private static final int UNLIMITED = 0;
    private static final int EXPAND_DELAY_MS = 300;

    private final Status status;
    private final int maxLines;
    private final Timer expandTimer = new Timer(EXPAND_DELAY_MS, event -> expand());
    private String fullText;
    private JTextArea textArea;
    private boolean truncated;
    private JBPopup expandedPopup;

    public WrappingEditorNotificationPanel(@NotNull FileEditor fileEditor, @NotNull String text) {
        this(fileEditor, text, UNLIMITED);
    }

    /**
     * Creates a warning banner showing at most {@code maxLines} lines of the text.
     */
    public WrappingEditorNotificationPanel(@NotNull FileEditor fileEditor, @NotNull String text, int maxLines) {
        super(fileEditor, Status.Warning);
        this.status = Status.Warning;
        this.maxLines = maxLines;
        installWrappingText(text);
    }

    public WrappingEditorNotificationPanel(@NotNull Status status, @NotNull String text) {
        super(status);
        this.status = status;
        this.maxLines = UNLIMITED;
        installWrappingText(text);
    }

    /**
     * Replaces the banner message, keeping the wrapping behaviour of the initial text.
     */
    public void setWrappingText(@NotNull String text) {
        fullText = text;
        if (textArea == null) {
            setText(text);
            return;
        }
        collapse();
        textArea.setText(text);
        if (isClamped()) {
            clampToWidth();
        }
        textArea.revalidate();
        revalidate();
        repaint();
    }

    @Override
    public void removeNotify() {
        collapse();
        super.removeNotify();
    }

    /**
     * Whether the banner cuts its message, and therefore expands on hover.
     */
    boolean isTruncated() {
        return truncated;
    }

    /**
     * The full-size banner shown over the editor while the collapsed one is hovered.
     */
    @NotNull
    JComponent createExpandedBanner() {
        return new WrappingEditorNotificationPanel(status, fullText);
    }

    private boolean isClamped() {
        return maxLines > UNLIMITED;
    }

    private void installWrappingText(@NotNull String text) {
        fullText = text;
        Container parent = myTextLabel.getParent();
        if (parent == null) {
            setText(text);
            return;
        }
        JTextArea area = isClamped() ? createClampedArea(text) : createArea(text);
        parent.remove(myTextLabel);
        parent.add(area, BorderLayout.CENTER);
        textArea = area;

        area.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                if (isClamped()) {
                    clampToWidth();
                }
                area.revalidate();
                revalidate();
            }
        });
        if (isClamped()) {
            installHoverExpansion(area);
        }
    }

    private void installHoverExpansion(@NotNull JTextArea area) {
        expandTimer.setRepeats(false);
        MouseAdapter hover = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent event) {
                if (truncated && expandedPopup == null) {
                    expandTimer.restart();
                }
            }

            @Override
            public void mouseExited(MouseEvent event) {
                expandTimer.stop();
            }
        };
        addMouseListener(hover);
        area.addMouseListener(hover);
    }

    private void expand() {
        if (!truncated || !isShowing() || expandedPopup != null) {
            return;
        }
        JBPopup popup = ExpandedBannerPopup.show(this, createExpandedBanner());
        expandedPopup = popup;
        popup.addListener(new JBPopupListener() {
            @Override
            public void onClosed(@NotNull LightweightWindowEvent event) {
                if (expandedPopup == popup) {
                    expandedPopup = null;
                }
            }
        });
    }

    private void collapse() {
        expandTimer.stop();
        if (expandedPopup != null) {
            JBPopup popup = expandedPopup;
            expandedPopup = null;
            popup.cancel();
        }
    }

    private void clampToWidth() {
        Insets insets = textArea.getInsets();
        int width = textArea.getWidth() - insets.left - insets.right;
        FontMetrics metrics = textArea.getFontMetrics(textArea.getFont());
        BannerTextClamp.Clamped clamped = BannerTextClamp.clamp(fullText, maxLines, width, metrics::stringWidth);
        if (!clamped.text().equals(textArea.getText())) {
            textArea.setText(clamped.text());
        }
        truncated = clamped.truncated();
        if (!truncated) {
            collapse();
        }
    }

    private JTextArea createArea(@NotNull String text) {
        JTextArea area = new JTextArea(text) {
            @Override
            public Dimension getPreferredSize() {
                Dimension preferred = super.getPreferredSize();
                int width = getWidth();
                if (width <= 0) {
                    return preferred;
                }
                setSize(width, Short.MAX_VALUE);
                Dimension wrapped = super.getPreferredSize();
                return new Dimension(width, wrapped.height);
            }
        };
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        return styled(area);
    }

    private JTextArea createClampedArea(@NotNull String text) {
        JTextArea area = new JTextArea(text) {
            @Override
            public Dimension getPreferredSize() {
                Dimension preferred = super.getPreferredSize();
                Insets insets = getInsets();
                int maxHeight = maxLines * getRowHeight() + insets.top + insets.bottom;
                return new Dimension(Math.max(getWidth(), 0), Math.min(preferred.height, maxHeight));
            }
        };
        area.setLineWrap(false);
        return styled(area);
    }

    private JTextArea styled(@NotNull JTextArea area) {
        area.setEditable(false);
        area.setOpaque(false);
        area.setBorder(JBUI.Borders.empty());
        area.setFont(myTextLabel.getFont());
        area.setForeground(myTextLabel.getForeground());
        return area;
    }
}
