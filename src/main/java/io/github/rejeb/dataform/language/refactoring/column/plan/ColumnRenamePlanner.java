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
package io.github.rejeb.dataform.language.refactoring.column.plan;

import com.intellij.openapi.project.Project;
import io.github.rejeb.dataform.language.refactoring.column.target.ColumnRenameSubject;
import org.jetbrains.annotations.NotNull;

/**
 * Decides everything a column rename is going to do, before anything is written.
 *
 * <p>Reads the column lineage graph and searches the project, so it must run in a background read
 * action and never on the event thread.</p>
 */
public interface ColumnRenamePlanner {

    static ColumnRenamePlanner getInstance(@NotNull Project project) {
        return project.getService(ColumnRenamePlanner.class);
    }

    /**
     * The plan for renaming the column of {@code subject} to {@code newName}. Stops at every column
     * produced by a star, reporting it in {@link ColumnRenamePlan#starBoundaries()} without an edit.
     */
    @NotNull
    ColumnRenamePlan plan(@NotNull ColumnRenameSubject subject, @NotNull String newName);

    /**
     * The plan again, with the user's answer about the columns produced by a star applied.
     */
    @NotNull
    ColumnRenamePlan resolve(@NotNull ColumnRenamePlan plan, @NotNull StarResolution resolution);
}
