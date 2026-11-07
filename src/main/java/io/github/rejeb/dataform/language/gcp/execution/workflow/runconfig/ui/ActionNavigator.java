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

import io.github.rejeb.dataform.language.gcp.execution.workflow.model.InvocationActionResult;
import org.jetbrains.annotations.NotNull;

/**
 * What the detail of an action asks the run console to do.
 */
interface ActionNavigator {

    /**
     * Does nothing, for a detail shown outside a run console.
     */
    ActionNavigator NONE = new ActionNavigator() {
        @Override
        public void openSource(@NotNull InvocationActionResult action) {
        }

        @Override
        public void run(@NotNull InvocationActionResult action) {
        }

        @Override
        public void select(@NotNull String target) {
        }
    };

    /**
     * Opens the SQLX file of an action.
     */
    void openSource(@NotNull InvocationActionResult action);

    /**
     * Runs an action alone.
     */
    void run(@NotNull InvocationActionResult action);

    /**
     * Selects the action with the given target in the run tree.
     */
    void select(@NotNull String target);
}
