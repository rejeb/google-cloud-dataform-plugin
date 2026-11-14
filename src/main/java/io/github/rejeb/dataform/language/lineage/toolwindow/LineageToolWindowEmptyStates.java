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

import com.intellij.util.ui.StatusText;
import io.github.rejeb.dataform.language.ui.EmptyStates;
import org.jetbrains.annotations.NotNull;

/**
 * The empty states the tabs of the Dataform Lineage tool window share.
 */
final class LineageToolWindowEmptyStates {

    static final String FROM_KNOWLEDGE_CATALOG = "Runs, trace and differences come from Knowledge Catalog";
    static final String SHOW_KNOWLEDGE_CATALOG = "Show the Knowledge Catalog lineage";
    static final String SELECT_A_TABLE = "Select a table in the lineage view";

    private LineageToolWindowEmptyStates() {
    }

    /**
     * Shows why a tab has nothing to list. A tab that is ready or that failed writes its own text.
     *
     * @param showCatalog opens the lineage editor on the Knowledge Catalog source
     */
    static void show(@NotNull StatusText text, @NotNull LineageToolWindowState state, @NotNull Runnable showCatalog) {
        switch (state) {
            case NO_EDITOR, DATAFORM_SOURCE -> EmptyStates.show(text, FROM_KNOWLEDGE_CATALOG, SHOW_KNOWLEDGE_CATALOG, showCatalog);
            case NO_SELECTION -> EmptyStates.show(text, SELECT_A_TABLE);
            default -> text.clear();
        }
    }
}
