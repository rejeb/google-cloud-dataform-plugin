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
package io.github.rejeb.dataform.language.schema.sql.usages;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * Reads the rows of the column window away from the event thread.
 *
 * <p>What fills the window is the reference search Find Usages runs: for every file the column's
 * name appears in, the injected SQL is built and the references there are resolved. Running that
 * where the gesture arrives holds the event thread for as long as it takes, with no progress and
 * nothing to cancel. A non-blocking read action gives the lock up whenever a write action is
 * requested and starts again afterwards, which is what lets the user keep typing while the window is
 * being filled.</p>
 *
 * <p>Two gestures in a row collapse into one. The second run replaces the first, so a click made
 * while a search is still running does not leave two windows racing to open.</p>
 */
final class ColumnUsageRowsLoader {

    private ColumnUsageRowsLoader() {
    }

    /**
     * Collects the rows for a column, the reads up to {@code maxReads}, and hands them to
     * {@code onRows} on the event thread. Nothing is handed over once the project is closed or the
     * editor the gesture came from is gone.
     */
    static void load(@NotNull Project project,
                     @NotNull Editor editor,
                     @NotNull ColumnWindowTarget target,
                     @Nullable PsiElement caretReference,
                     int maxReads,
                     @NotNull Consumer<List<ColumnUsageRow>> onRows) {
        ReadAction.nonBlocking(() -> ColumnUsageRows.of(project, target, caretReference, maxReads))
                .expireWith(project)
                .expireWhen(editor::isDisposed)
                .coalesceBy(ColumnUsageRowsLoader.class, editor)
                .finishOnUiThread(ModalityState.defaultModalityState(), onRows)
                .submit(AppExecutorUtil.getAppExecutorService());
    }
}
