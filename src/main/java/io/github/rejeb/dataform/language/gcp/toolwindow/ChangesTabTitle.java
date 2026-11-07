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
package io.github.rejeb.dataform.language.gcp.toolwindow;

import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.ui.ColorUtil;
import com.intellij.util.ui.NamedColorUtil;
import org.jetbrains.annotations.NotNull;

/**
 * The title of the Changes tab: its name, followed by the number of changes in gray, as the tabs
 * of the Problems tool window.
 */
public final class ChangesTabTitle {

    private static final String NAME = "Changes";

    private ChangesTabTitle() {
    }

    /**
     * The title for the given number of changes; the bare name when there is none.
     */
    public static @NotNull String of(int count) {
        if (count <= 0) {
            return NAME;
        }
        HtmlChunk number = HtmlChunk.text(String.valueOf(count))
                .wrapWith(HtmlChunk.tag("font").attr("color", ColorUtil.toHtmlColor(NamedColorUtil.getInactiveTextColor())));
        return new HtmlBuilder().append(NAME).nbsp().append(number).wrapWithHtmlBody().toString();
    }
}
