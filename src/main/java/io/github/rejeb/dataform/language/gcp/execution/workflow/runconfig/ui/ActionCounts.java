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

import java.util.List;

/**
 * How many actions of an invocation ended each way; the actions still to end are remaining.
 */
record ActionCounts(int succeeded, int failed, int skipped, int cancelled, int remaining) {

    /**
     * Counts the given actions by outcome.
     */
    @NotNull
    static ActionCounts of(@NotNull List<InvocationActionResult> actions) {
        int succeeded = 0;
        int failed = 0;
        int skipped = 0;
        int cancelled = 0;
        int remaining = 0;
        for (InvocationActionResult action : actions) {
            switch (action.state()) {
                case SUCCEEDED -> succeeded++;
                case FAILED -> failed++;
                case SKIPPED, DISABLED -> skipped++;
                case CANCELLED -> cancelled++;
                case PENDING, RUNNING, UNKNOWN -> remaining++;
            }
        }
        return new ActionCounts(succeeded, failed, skipped, cancelled, remaining);
    }

    /**
     * The actions that ended, whatever their outcome.
     */
    int finished() {
        return succeeded + failed + skipped + cancelled;
    }

    /**
     * All the actions of the invocation.
     */
    int total() {
        return finished() + remaining;
    }
}
