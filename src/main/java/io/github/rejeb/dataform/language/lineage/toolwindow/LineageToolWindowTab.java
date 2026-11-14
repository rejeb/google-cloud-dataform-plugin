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
package io.github.rejeb.dataform.language.lineage.toolwindow;

import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.ui.ColorUtil;
import com.intellij.util.ui.NamedColorUtil;
import org.jetbrains.annotations.NotNull;

/**
 * The tabs of the Dataform Lineage tool window.
 */
public enum LineageToolWindowTab {
    RUNS("Runs"),
    TRACE("Trace"),
    DIFFERENCES("Differences");

    private final String title;

    LineageToolWindowTab(@NotNull String title) {
        this.title = title;
    }

    /**
     * @return the title of the Differences tab for a number of differences: the bare name when
     * there is none, the name followed by the grayed count otherwise
     */
    public static @NotNull String differencesTitle(int count) {
        if (count <= 0) return DIFFERENCES.title;
        HtmlChunk number = HtmlChunk.text(String.valueOf(count))
                .wrapWith(HtmlChunk.tag("font").attr("color", ColorUtil.toHtmlColor(NamedColorUtil.getInactiveTextColor())));
        return new HtmlBuilder().append(DIFFERENCES.title).nbsp().append(number).wrapWithHtmlBody().toString();
    }

    /**
     * @return the title of the tab
     */
    public @NotNull String title() {
        return title;
    }
}
