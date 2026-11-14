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

import com.intellij.openapi.project.Project;
import io.github.rejeb.dataform.language.lineage.model.LineageModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The Dataform Lineage tool window: the runs, the trace and the differences of what is selected in
 * the project lineage editor with the Knowledge Catalog source. Its methods are called on the event
 * dispatch thread.
 */
public interface LineageToolWindow {

    String ID = "Dataform Lineage";

    static LineageToolWindow getInstance(@NotNull Project project) {
        return project.getService(LineageToolWindow.class);
    }

    /**
     * Makes the tool window follow the model of a lineage editor.
     */
    void attach(@NotNull LineageModel model);

    /**
     * Stops following the model of a lineage editor that is closed.
     */
    void detach(@NotNull LineageModel model);

    /**
     * Shows the tool window without giving it the focus.
     *
     * @param tab the tab to show, {@code null} to keep the one it last showed
     */
    void show(@Nullable LineageToolWindowTab tab);

    /**
     * Hides the tool window; its stripe button stays.
     */
    void hide();
}
