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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.BigQueryJobDetails;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.InvocationActionResult;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.InvocationActionState;
import io.github.rejeb.dataform.language.gcp.service.DataformGcpService;
import io.github.rejeb.dataform.language.gcp.settings.GcpRepositorySettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * The BigQuery job details of the actions of one workflow run, shared by the run totals and the
 * Summary and Details tabs of an action. Details of a finished action are fetched once.
 */
public final class ActionJobDetailsCache {

    private static final Logger LOG = Logger.getInstance(ActionJobDetailsCache.class);
    private static final int MAX_PARALLEL_LOADS = 8;
    private static final Set<InvocationActionState> TERMINAL = EnumSet.of(
            InvocationActionState.SUCCEEDED,
            InvocationActionState.FAILED,
            InvocationActionState.CANCELLED,
            InvocationActionState.SKIPPED,
            InvocationActionState.DISABLED);

    private final BiFunction<InvocationActionResult, String, BigQueryJobDetails> loader;
    private final Function<InvocationActionResult, String> locationResolver;
    private final Executor executor;
    private final Map<String, Entry> entries = new HashMap<>();

    /**
     * Creates the cache of one run, fetching through {@link DataformGcpService}.
     */
    public ActionJobDetailsCache(@NotNull Project project) {
        this((action, location) -> fetch(project, action, location),
                action -> resolveLocation(project, action),
                AppExecutorUtil.createBoundedApplicationPoolExecutor("Dataform job details", MAX_PARALLEL_LOADS));
    }

    ActionJobDetailsCache(@NotNull BiFunction<InvocationActionResult, String, BigQueryJobDetails> loader,
                          @NotNull Function<InvocationActionResult, String> locationResolver,
                          @NotNull Executor executor) {
        this.loader = loader;
        this.locationResolver = locationResolver;
        this.executor = executor;
    }

    /**
     * Returns the job details of an action, fetched in the background; null when the action has no
     * job or the details cannot be fetched.
     */
    @NotNull
    public CompletableFuture<@Nullable BigQueryJobDetails> load(@NotNull InvocationActionResult action) {
        String jobId = action.jobId();
        if (jobId == null || action.jobProject() == null) {
            return CompletableFuture.completedFuture(null);
        }
        boolean terminal = TERMINAL.contains(action.state());
        Entry entry;
        synchronized (entries) {
            Entry existing = entries.get(jobId);
            if (existing != null && (existing.terminal() || !terminal)) {
                return existing.future();
            }
            entry = new Entry(new CompletableFuture<>(), terminal);
            entries.put(jobId, entry);
        }
        executor.execute(() -> {
            BigQueryJobDetails details = null;
            try {
                details = loader.apply(action, locationResolver.apply(action));
            } catch (RuntimeException e) {
                LOG.warn("Failed to load BigQuery job details for jobId=" + jobId, e);
            }
            if (details == null || !entry.terminal()) {
                synchronized (entries) {
                    entries.remove(jobId, entry);
                }
            }
            entry.future().complete(details);
        });
        return entry.future();
    }

    /**
     * Tells whether an action is over, so that its job details no longer change.
     */
    public static boolean isFinished(@NotNull InvocationActionResult action) {
        return TERMINAL.contains(action.state());
    }

    @Nullable
    private static BigQueryJobDetails fetch(@NotNull Project project,
                                            @NotNull InvocationActionResult action,
                                            @Nullable String location) {
        try {
            return DataformGcpService.getInstance(project)
                    .getJobDetails(action.jobId(), action.jobProject(), location);
        } catch (Exception e) {
            LOG.warn("Failed to load BigQuery job details for jobId=" + action.jobId()
                    + " project=" + action.jobProject() + " location=" + location, e);
            return null;
        }
    }

    @Nullable
    private static String resolveLocation(@NotNull Project project, @NotNull InvocationActionResult action) {
        if (action.jobProject() != null && action.jobDataset() != null) {
            String datasetLocation = DataformGcpService.getInstance(project)
                    .resolveDatasetLocation(action.jobProject(), action.jobDataset());
            if (datasetLocation != null) return datasetLocation;
        }
        if (action.jobLocation() != null && !action.jobLocation().isBlank()) return action.jobLocation();
        String configured = GcpRepositorySettings.getInstance(project).getLocation();
        if (configured != null && !configured.isBlank()) return configured;
        LOG.warn("No location could be resolved for action " + action.target()
                + ", letting BigQuery resolve it.");
        return null;
    }

    private record Entry(@NotNull CompletableFuture<BigQueryJobDetails> future, boolean terminal) {
    }
}
