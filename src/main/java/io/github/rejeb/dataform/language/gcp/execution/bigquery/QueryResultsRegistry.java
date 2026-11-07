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
package io.github.rejeb.dataform.language.gcp.execution.bigquery;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Project-level service storing the latest BigQuery query run per table, from its start to its
 * result. Acts as the source of truth for the Services view nodes.
 */
@Service(Service.Level.PROJECT)
public final class QueryResultsRegistry {

    private final Map<String, QueryRun> runs = new LinkedHashMap<>();

    public static QueryResultsRegistry getInstance(@NotNull Project project) {
        return project.getService(QueryResultsRegistry.class);
    }

    /**
     * Lists a run that has just started, in place of the last run of the same table.
     */
    public synchronized void start(@NotNull QueryRun run) {
        release(runs.put(run.tableName(), run));
    }

    /**
     * Records the result of a started run and returns the finished run, or {@code null} when the
     * run was removed or followed by another run of its table; the result is then released.
     */
    public synchronized @Nullable QueryRun finish(@NotNull QueryRun run, @NotNull BigQueryJobResult result,
                                                  boolean cancelled) {
        if (runs.get(run.tableName()) != run) {
            disposePages(result);
            return null;
        }
        QueryRun finished = run.finished(result, cancelled);
        runs.put(run.tableName(), finished);
        return finished;
    }

    public synchronized @NotNull List<QueryRun> getAll() {
        return new ArrayList<>(runs.values());
    }

    public synchronized @Nullable QueryRun get(@NotNull String tableName) {
        return runs.get(tableName);
    }

    /**
     * Removes the run of the table, cancelling it when it is still running.
     */
    public synchronized void remove(@NotNull String tableName) {
        release(runs.remove(tableName));
    }

    /**
     * Whether a run is over and can be removed by {@link #removeFinished()}.
     */
    public synchronized boolean hasFinished() {
        return runs.values().stream().anyMatch(QueryRun::isFinished);
    }

    /**
     * Removes every finished run and keeps the running ones.
     */
    public synchronized void removeFinished() {
        runs.values().removeIf(run -> {
            if (!run.isFinished()) {
                return false;
            }
            release(run);
            return true;
        });
    }

    private static void release(@Nullable QueryRun run) {
        if (run == null) {
            return;
        }
        if (run.cancel() != null) {
            run.cancel().run();
        }
        if (run.result() != null) {
            disposePages(run.result());
        }
    }

    private static void disposePages(@NotNull BigQueryJobResult result) {
        if (result.pagedResult() != null) {
            result.pagedResult().dispose();
        }
    }
}
