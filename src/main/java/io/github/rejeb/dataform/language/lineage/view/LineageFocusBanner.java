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

import com.intellij.ui.InlineBanner;
import com.intellij.openapi.util.text.StringUtil;
import io.github.rejeb.dataform.language.lineage.graph.LineageNode;
import io.github.rejeb.dataform.language.lineage.model.LineageModel;
import io.github.rejeb.dataform.language.ui.Banners;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;

/**
 * The banner floating over the top of the lineage canvas while it is focused on one action: how
 * many actions are upstream and downstream of it, and a link leaving focus mode.
 */
final class LineageFocusBanner {

    private final LineageModel model;
    private final InlineBanner banner = Banners.info("");
    private final JPanel view = new JPanel(new BorderLayout());
    private String message = "";

    LineageFocusBanner(@NotNull LineageModel model) {
        this.model = model;
        banner.addAction("Exit focus", model::exitFocus);
        view.setOpaque(false);
        view.add(banner, BorderLayout.CENTER);
        refresh();
        model.addListener(m -> refresh());
    }

    /**
     * The banner, hidden while the view is not focused.
     */
    @NotNull
    InlineBanner component() {
        return banner;
    }

    /**
     * The banner as laid over the canvas, hidden with the banner.
     */
    @NotNull
    JComponent view() {
        return view;
    }

    /**
     * The HTML message of the banner.
     */
    @NotNull
    String message() {
        return message;
    }

    private void refresh() {
        String focusId = model.focusId();
        banner.setVisible(focusId != null);
        view.setVisible(focusId != null);
        if (focusId == null) return;
        LineageNode node = model.graph().node(focusId);
        String name = node != null ? node.schema() + "." + node.name() : focusId;
        String text = "Focused on <b>" + StringUtil.escapeXmlEntities(name) + "</b>: "
                + model.ancestors(focusId).size() + " upstream, "
                + model.descendants(focusId).size() + " downstream";
        if (!text.equals(message)) {
            message = text;
            banner.setMessage(text);
        }
    }
}
