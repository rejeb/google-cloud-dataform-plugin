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
package io.github.rejeb.dataform.language.fileEditor;

import com.intellij.icons.AllIcons;
import com.intellij.ui.components.ActionLink;
import com.intellij.util.ui.JBFont;
import io.github.rejeb.dataform.language.ui.PreviewHeader;
import io.github.rejeb.dataform.language.ui.UiText;
import org.jetbrains.annotations.NotNull;

import java.awt.event.ActionListener;

/**
 * The row above the sections of the query preview: how many actions the file compiles to and when
 * it was compiled, a link to the Build window when the last compilation failed, and the estimated
 * cost of the file at the right.
 */
final class QueryPreviewHeader extends PreviewHeader {

    private static final String COMPILE_FAILED = "last compile failed";
    private static final String COMPILE_FAILED_TOOLTIP = "The last compilation failed. Show it in the Build window.";

    private final ActionLink compileFailed;

    /**
     * @param total     the estimated cost of the file
     * @param showBuild opens the Build window, from the link shown when the last compilation failed
     */
    QueryPreviewHeader(@NotNull QueryCostTotalBar total, @NotNull Runnable showBuild) {
        compileFailed = new ActionLink(COMPILE_FAILED, (ActionListener) e -> showBuild.run());
        compileFailed.setIcon(AllIcons.General.Warning);
        compileFailed.setFont(JBFont.medium());
        compileFailed.setToolTipText(COMPILE_FAILED_TOOLTIP);
        compileFailed.setVisible(false);

        addLeading(compileFailed);
        setTrailing(total);
    }

    /**
     * @param actions           the number of actions shown
     * @param compiledAt        when the graph was compiled, in epoch milliseconds, or a negative value when unknown
     * @param lastCompileFailed whether the last run of the Dataform CLI compiled nothing
     */
    void showSummary(int actions, long compiledAt, boolean lastCompileFailed) {
        String count = actions == 1 ? "1 action" : actions + " actions";
        compileFailed.setVisible(lastCompileFailed);
        setSummary(compiledAt > 0 ? UiText.dot(count, "compiled " + clock(compiledAt)) : count);
    }

    @NotNull
    ActionLink compileFailedLink() {
        return compileFailed;
    }
}
