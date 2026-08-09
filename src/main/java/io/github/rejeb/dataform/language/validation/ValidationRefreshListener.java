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
package io.github.rejeb.dataform.language.validation;

import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.util.concurrency.AppExecutorUtil;
import io.github.rejeb.dataform.language.diagnostics.DataformEditorRefresher;
import io.github.rejeb.dataform.language.diagnostics.ValidationProblemInlayManager;
import io.github.rejeb.dataform.language.util.DataformProjects;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Rebuilds the validation chips once the user stops typing. Each edit pushes the rebuild back by
 * the quiet period, so problems reported against half-typed text never reach the editor, and the
 * daemon is restarted at the same time so {@link DataformValidationAnnotator} paints the same
 * problems in the same pass.
 */
public final class ValidationRefreshListener implements DocumentListener {

    static final long DEBOUNCE_MS = DataformEditActivityService.QUIET_PERIOD_MS;

    private final AtomicLong generation = new AtomicLong();

    @Override
    public void documentChanged(@NotNull DocumentEvent event) {
        scheduleRefresh(generation.incrementAndGet(), DEBOUNCE_MS);
    }

    /**
     * Waits out the quiet period before rebuilding. The wait is re-checked when it elapses rather
     * than assumed: a rebuild starting a few milliseconds early would find the user still editing,
     * be skipped by the annotator, and leave the problems unpainted until the next keystroke.
     */
    private void scheduleRefresh(long requested, long delayMs) {
        AppExecutorUtil.getAppScheduledExecutorService().schedule(() -> {
            if (generation.get() != requested) {
                return;
            }
            if (rescheduleWhileEditing(requested)) {
                return;
            }
            DataformProjects.forEachOpen(this::refresh);
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Whether any project is still within its quiet period, in which case the rebuild is pushed
     * back to the moment the user actually stops typing.
     */
    private boolean rescheduleWhileEditing(long requested) {
        long remaining = 0;
        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
            if (!project.isDisposed()) {
                remaining = Math.max(remaining,
                        DataformEditActivityService.getInstance(project).remainingQuietPeriodMs());
            }
        }
        if (remaining <= 0) {
            return false;
        }
        scheduleRefresh(requested, remaining);
        return true;
    }

    private void refresh(@NotNull Project project) {
        ValidationProblemInlayManager.getInstance(project).refreshAll();
        DataformEditorRefresher.refresh(project);
    }
}
