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

import com.intellij.build.BuildContentManager;
import com.intellij.build.BuildViewManager;
import com.intellij.build.DefaultBuildDescriptor;
import com.intellij.build.FilePosition;
import com.intellij.build.events.FinishBuildEvent;
import com.intellij.build.events.MessageEvent;
import com.intellij.build.events.StartBuildEvent;
import com.intellij.build.events.impl.FailureResultImpl;
import com.intellij.build.events.impl.SuccessResultImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.util.ThreeState;
import io.github.rejeb.dataform.language.diagnostics.sql.bigquery.DryRunBuildProblem;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Lists the action queries that fail since a column change in the Build tool window, under their
 * files, the way the compilation reports its errors.
 */
final class ColumnImpactBuildView {

    private static final String IMPACT_ANALYSIS = "Dataform Impact Analysis";

    private ColumnImpactBuildView() {
    }

    /**
     * Lists the action queries that fail since the changes under their files, the way the
     * compilation lists its errors, and brings the Build tool window forward with the focus in it,
     * whether it was closed, minimized or behind another window.
     */
    static void show(@NotNull Project project, @NotNull List<ColumnImpact> impacts) {
        if (project.isDisposed()) return;
        ToolWindow toolWindow = BuildContentManager.getInstance(project).getOrCreateToolWindow();
        BuildViewManager view = project.getService(BuildViewManager.class);
        Object buildId = new Object();
        VirtualFile projectDir = ProjectUtil.guessProjectDir(project);
        DefaultBuildDescriptor descriptor = descriptor(buildId, projectDir != null ? projectDir.getPath() : "");
        view.onEvent(buildId, StartBuildEvent.builder("Analyzing column impacts...", descriptor).build());
        messagesByFile(buildId, impacts).forEach(event -> view.onEvent(buildId, event));
        int failing = impacts.stream().mapToInt(i -> i.affected().size()).sum();
        view.onEvent(buildId, FinishBuildEvent.builder(buildId, summaryOf(impacts.size(), failing),
                        failing == 0 ? new SuccessResultImpl() : new FailureResultImpl())
                .withTime(System.currentTimeMillis())
                .build());
        toolWindow.activate(null);
    }

    /**
     * The descriptor of the report: its tab is selected when added, and the errors are listed
     * without opening the file of the first one.
     */
    static @NotNull DefaultBuildDescriptor descriptor(@NotNull Object buildId, @NotNull String workDir) {
        DefaultBuildDescriptor descriptor = new DefaultBuildDescriptor(buildId, IMPACT_ANALYSIS, workDir,
                System.currentTimeMillis());
        descriptor.setActivateToolWindowWhenAdded(true);
        descriptor.setNavigateToError(ThreeState.NO);
        return descriptor;
    }

    /**
     * One error per failing action query, carrying the position of the error in its file so the
     * Build tool window lists it under that file, and one information line per change no query
     * fails on.
     */
    static @NotNull List<MessageEvent> messagesByFile(@NotNull Object buildId, @NotNull List<ColumnImpact> impacts) {
        List<MessageEvent> events = new ArrayList<>();
        for (ColumnImpact impact : impacts) {
            if (impact.affected().isEmpty()) {
                events.add(MessageEvent.builder(impact.change().describe(), MessageEvent.Kind.INFO)
                        .withParentId(buildId)
                        .withGroup(IMPACT_ANALYSIS)
                        .build());
            }
            for (DryRunBuildProblem failure : impact.affected()) {
                var event = MessageEvent.builder("BigQuery: " + failure.message() + " — " + impact.change().describe(),
                                MessageEvent.Kind.ERROR)
                        .withParentId(buildId)
                        .withGroup(IMPACT_ANALYSIS)
                        .withDescription(failure.actionFullName() + "\n" + failure.details());
                VirtualFile file = failure.file();
                if (file != null) {
                    event = event.withFilePosition(new FilePosition(file.toNioPath(), failure.line(), failure.column()));
                }
                events.add(event.build());
            }
        }
        return events;
    }

    private static @NotNull String summaryOf(int changes, int failing) {
        return changes + " column change(s), " + failing + " related action quer"
                + (failing == 1 ? "y fails" : "ies fail");
    }
}
