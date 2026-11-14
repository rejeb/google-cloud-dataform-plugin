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

import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.SwingConstants;
import javax.swing.plaf.LabelUI;

/**
 * The comment shown under a form field, drawn as the platform draws it: context help colour and
 * font, copyable, wrapped at about 70 characters. It replaces
 * {@code ComponentPanelBuilder.createCommentComponent}, which is deprecated.
 */
public final class CommentLabel extends JBLabel {

    private static final int MAX_LINE_LENGTH = 70;

    /**
     * @param text the comment, as plain text
     */
    public CommentLabel(@NotNull String text) {
        super("");
        setForeground(JBUI.CurrentTheme.ContextHelp.FOREGROUND);
        setCopyable(true);
        setAllowAutoWrapping(true);
        setVerticalTextPosition(SwingConstants.TOP);
        setFocusable(false);
        setText(html(text));
    }

    @Override
    public void setUI(LabelUI ui) {
        super.setUI(ui);
        setFont(JBFont.medium());
    }

    private @NotNull String html(@NotNull String text) {
        HtmlChunk.Element block = HtmlChunk.div();
        if (text.length() > MAX_LINE_LENGTH) {
            block = block.attr("width", getFontMetrics(getFont()).stringWidth(text.substring(0, MAX_LINE_LENGTH)));
        }
        return new HtmlBuilder().append(HtmlChunk.text(text).wrapWith(block)).wrapWithHtmlBody().toString();
    }
}
