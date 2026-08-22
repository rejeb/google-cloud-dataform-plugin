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
package io.github.rejeb.dataform.language.diagnostics;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.EditorNotifications;
import org.jetbrains.annotations.NotNull;

/**
 * Repaints what shows Dataform diagnostics once they have changed: the annotations produced by the
 * daemon and the banners above the editors. Both live on the EDT, so callers can invoke this from
 * any thread.
 *
 * <p>The daemon is left alone under a test. Nothing there watches the editor for a repaint: a test
 * that wants annotations asks for them, and running them itself is what it measures. The restart
 * would only ever arrive in the middle of that, where the platform refuses a model change and
 * fails whichever test happened to be highlighting — a file created by one test restarting the
 * daemon under the next.</p>
 */
public final class DataformEditorRefresher {

    private DataformEditorRefresher() {
    }

    /**
     * Restarts the daemon and refreshes the editor banners of the project.
     */
    public static void refresh(@NotNull Project project) {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (project.isDisposed()) {
                return;
            }
            if (!ApplicationManager.getApplication().isUnitTestMode()) {
                DaemonCodeAnalyzer.getInstance(project).restart();
            }
            EditorNotifications.getInstance(project).updateAllNotifications();
        });
    }
}
