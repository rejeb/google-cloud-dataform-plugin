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
package io.github.rejeb.dataform.language.fileEditor.cost;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The cost of the last run of each action whose bytes a dry-run cannot evaluate, kept while the
 * project is open.
 */
public interface QueryRunCosts {

    /**
     * Returns the instance of the project.
     */
    static QueryRunCosts getInstance(@NotNull Project project) {
        return project.getService(QueryRunCosts.class);
    }

    /**
     * Keeps the cost of a run in place of the cost of the run before.
     *
     * @param action full name of the action that ran
     */
    void record(@NotNull String action, @NotNull RunCost cost);

    /**
     * Returns the cost of the last run of the action, null when it did not run.
     *
     * @param action full name of the action
     */
    @Nullable
    RunCost of(@NotNull String action);
}
