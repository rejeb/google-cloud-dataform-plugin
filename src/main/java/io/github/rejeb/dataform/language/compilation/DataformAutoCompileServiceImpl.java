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
package io.github.rejeb.dataform.language.compilation;

import com.intellij.codeInsight.lookup.LookupManagerListener;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.diagnostics.CompilationDiagnosticService;
import io.github.rejeb.dataform.language.diagnostics.DataformEditorRefresher;
import io.github.rejeb.dataform.language.diagnostics.ValidationProblemInlayManager;
import io.github.rejeb.dataform.language.schema.sql.DataformTableSchemaService;
import io.github.rejeb.dataform.language.settings.DataformToolsSettings;
import io.github.rejeb.dataform.language.validation.DataformEditActivityService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Default {@link DataformAutoCompileService}, debouncing saves onto a single background
 * compilation.
 */
public final class DataformAutoCompileServiceImpl implements DataformAutoCompileService, Disposable {

    private static final Logger LOG = Logger.getInstance(DataformAutoCompileServiceImpl.class);
    private static final long SAVE_DEBOUNCE_MS = 300;
    static final long QUIET_PERIOD_MS = DataformEditActivityService.QUIET_PERIOD_MS;
    private static final long COMPLETION_RETRY_MS = 1_000;

    private final Project project;
    private final AtomicLong generation = new AtomicLong();
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean rerunRequested = new AtomicBoolean();
    private volatile boolean completionVisible;

    public DataformAutoCompileServiceImpl(@NotNull Project project) {
        this.project = project;
        project.getMessageBus().connect(this).subscribe(LookupManagerListener.TOPIC,
                (LookupManagerListener) (oldLookup, newLookup) -> completionVisible = newLookup != null);
    }

    @Override
    public void scheduleCompile() {
        schedule(SAVE_DEBOUNCE_MS);
    }

    @Override
    public void scheduleCompileAfterEdit() {
        DataformEditActivityService.getInstance(project).noteEdit();
        schedule(QUIET_PERIOD_MS);
    }

    private void schedule(long delayMs) {
        if (project.isDisposed() || !DataformToolsSettings.getInstance().isCompileOnSave()) {
            return;
        }
        if (running.get()) {
            rerunRequested.set(true);
            return;
        }
        scheduleFire(generation.incrementAndGet(), delayMs);
    }

    private void scheduleFire(long requested, long delayMs) {
        AppExecutorUtil.getAppScheduledExecutorService().schedule(
                () -> fire(requested), delayMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Starts the compilation unless the user would be disturbed by it: an edit newer than the quiet
     * period means they are still typing, and an open completion popup would be closed by the
     * diagnostics the compilation refreshes. Both cases push the run back instead of dropping it.
     */
    private void fire(long requested) {
        if (project.isDisposed() || generation.get() != requested) {
            return;
        }
        long remainingQuietPeriod =
                DataformEditActivityService.getInstance(project).remainingQuietPeriodMs();
        if (remainingQuietPeriod > 0) {
            scheduleFire(requested, remainingQuietPeriod);
            return;
        }
        if (completionVisible) {
            scheduleFire(requested, COMPLETION_RETRY_MS);
            return;
        }
        if (!running.compareAndSet(false, true)) {
            rerunRequested.set(true);
            return;
        }
        AppExecutorUtil.getAppExecutorService().execute(this::compileNow);
    }

    @Override
    public void dispose() {
    }

    private void compileNow() {
        long startedAt = System.currentTimeMillis();
        try {
            if (project.isDisposed()) {
                return;
            }
            CompiledGraph graph = DataformCompilationService.getInstance(project).compile(false);
            LOG.info("Automatic Dataform compilation finished in "
                    + (System.currentTimeMillis() - startedAt) + " ms");
            refreshSchemas(graph);
        } catch (Exception e) {
            LOG.warn("Automatic Dataform compilation failed", e);
        } finally {
            running.set(false);
            refreshEditors();
            if (rerunRequested.compareAndSet(true, false)) {
                scheduleCompile();
            }
        }
    }

    /**
     * Re-extracts the schemas the save invalidated. The refresh is incremental, so only the
     * actions whose source changed and the actions depending on them are dry-run again; actions
     * declared in a file that failed to compile keep their last known schema, and a compilation
     * that left no action standing is skipped entirely.
     */
    private void refreshSchemas(@Nullable CompiledGraph graph) {
        if (graph == null || project.isDisposed()) {
            return;
        }
        if (CompilationFailures.isTotalFailure(graph)) {
            LOG.info("Compilation failed as a whole, keeping the schemas already extracted");
            return;
        }
        DataformTableSchemaService.getInstance(project)
                .refreshAsync(graph, false, CompilationFailures.fileNamesOf(graph));
    }

    private void refreshEditors() {
        CompilationDiagnosticService.getInstance(project).invalidate();
        ValidationProblemInlayManager.getInstance(project).refreshAll();
        DataformEditorRefresher.refresh(project);
        ApplicationManager.getApplication().invokeLater(() -> {
            if (!project.isDisposed()) {
                ProjectView.getInstance(project).refresh();
            }
        });
    }
}
