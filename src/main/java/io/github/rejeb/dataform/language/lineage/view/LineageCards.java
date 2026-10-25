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
package io.github.rejeb.dataform.language.lineage.view;

import io.github.rejeb.dataform.language.lineage.model.LineageModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * The body of a lineage view, with the warning banner and the toolbar above the graph, and the
 * message shown in its place while there is no graph to show.
 */
final class LineageCards extends JPanel {

    private static final String CARD_EMPTY = "empty";
    private static final String CARD_BODY = "body";

    private final CardLayout layout;

    LineageCards(@NotNull LineageModel model,
                 @NotNull JComponent toolbar,
                 @Nullable JComponent west,
                 @NotNull JComponent center,
                 @NotNull JComponent statusBar,
                 @NotNull String emptyMessage) {
        this(new CardLayout());
        JPanel header = new JPanel(new BorderLayout());
        header.add(new LineageWarningBanner(model), BorderLayout.NORTH);
        header.add(toolbar, BorderLayout.CENTER);

        JPanel body = new JPanel(new BorderLayout());
        body.add(header, BorderLayout.NORTH);
        if (west != null) body.add(west, BorderLayout.WEST);
        body.add(center, BorderLayout.CENTER);
        body.add(statusBar, BorderLayout.SOUTH);

        add(LineageToolbarSupport.emptyCard(emptyMessage), CARD_EMPTY);
        add(body, CARD_BODY);
    }

    private LineageCards(@NotNull CardLayout layout) {
        super(layout);
        this.layout = layout;
    }

    void showEmpty(boolean empty) {
        layout.show(this, empty ? CARD_EMPTY : CARD_BODY);
    }
}
