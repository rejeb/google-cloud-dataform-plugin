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

import org.jetbrains.annotations.Nullable;

import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Container;

/**
 * The place of the Knowledge Catalog state banner above a lineage graph: hidden while there is
 * none, and laid out again with the overlay holding it when the banner changes.
 */
final class StateBannerSlot extends JPanel {

    private @Nullable DataplexStateBanner banner;

    StateBannerSlot() {
        super(new BorderLayout());
        setOpaque(false);
        setVisible(false);
    }

    void setBanner(@Nullable DataplexStateBanner banner) {
        if (this.banner == banner) return;
        this.banner = banner;
        removeAll();
        if (banner != null) add(banner.component(), BorderLayout.CENTER);
        setVisible(banner != null);
        revalidate();
        Container overlay = getParent() == null ? null : getParent().getParent();
        if (overlay != null) {
            overlay.revalidate();
            overlay.repaint();
        }
    }

    @Nullable DataplexStateBanner banner() {
        return banner;
    }
}
