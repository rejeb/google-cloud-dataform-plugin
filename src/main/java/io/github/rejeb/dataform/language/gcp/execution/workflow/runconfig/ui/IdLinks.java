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
package io.github.rejeb.dataform.language.gcp.execution.workflow.runconfig.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.ui.components.BrowserLink;
import com.intellij.util.ui.JBFont;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The links of the run console that show an id, such as a BigQuery job or a workflow invocation, and
 * open it in the Google Cloud console. They share the prototype's {@code link mono small} style: the
 * editor font at the small size, with the external-link arrow.
 */
final class IdLinks {

    private IdLinks() {
    }

    /**
     * A link showing the given id and opening the given URL, with the given tooltip.
     */
    @NotNull
    static BrowserLink create(@NotNull String id, @Nullable String url, @NotNull String tooltip) {
        BrowserLink link = new BrowserLink(AllIcons.Ide.External_link_arrow, id, tooltip, url != null ? url : "");
        link.setFont(EditorColorsManager.getInstance().getGlobalScheme().getFont(EditorFontType.PLAIN)
                .deriveFont(JBFont.medium().getSize2D()));
        return link;
    }
}
