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
package io.github.rejeb.dataform.language.columns.impact;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import io.github.rejeb.dataform.language.columns.rename.downstream.DownstreamRename;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Warns when a saved change removes, renames or retypes a column other actions read. Once the
 * compilation and the schemas have caught up with the save, the columns are compared with what
 * they were before it, and the dry-runs failing since are handed to the change behind them.
 * Every new set of impacts is counted, so what is computed from them can be cached against it.
 */
public interface ColumnImpactService extends ModificationTracker {

    static ColumnImpactService getInstance(@NotNull Project project) {
        return project.getService(ColumnImpactService.class);
    }

    /**
     * Called before a Dataform source is saved. Keeps the columns and the dry-run failures as they
     * are, unless a check is already waiting, and starts a check when none is running.
     */
    void noteSourceSaved();

    /**
     * The column change the current dry-run failure of an action comes from.
     *
     * @param actionFullName the full name of the failing action
     * @param failureMessage the message its dry-run fails with now
     * @return the change, or {@code null} when the failure is not one a change explains
     */
    @Nullable
    ColumnChange changeBehind(@NotNull String actionFullName, @NotNull String failureMessage);

    /**
     * The columns renamed in an action that downstream actions still fail to read under their old
     * name, as of their current dry-runs.
     *
     * @param tableFullName the full name of the action the columns were renamed in
     */
    @NotNull
    List<DownstreamRename> pendingRenamesOf(@NotNull String tableFullName);
}
