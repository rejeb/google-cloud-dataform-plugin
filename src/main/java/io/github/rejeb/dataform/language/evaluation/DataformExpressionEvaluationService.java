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
package io.github.rejeb.dataform.language.evaluation;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Evaluates Dataform template expressions with the project Node interpreter and caches the results.
 *
 * <p>An evaluation pass is asked for when a file is opened or comes back into focus, never because
 * it was edited: a value that changed under the person editing the hole would fold their code away
 * again on every keystroke. Every pass computes every value of the file anew — what the values
 * depend on may have moved anywhere in the meantime.</p>
 */
public interface DataformExpressionEvaluationService extends ModificationTracker {

    /**
     * Returns the project instance of the service.
     */
    static DataformExpressionEvaluationService getInstance(@NotNull Project project) {
        return project.getService(DataformExpressionEvaluationService.class);
    }

    /**
     * Returns the cached value of an expression, or {@code null} when it has not been evaluated or
     * could not be resolved. Never starts a process, so it is safe from a read action on the EDT.
     */
    @Nullable
    String getCachedValue(@NotNull VirtualFile file, @NotNull String expressionSource);

    /**
     * Schedules a debounced background evaluation pass for the given file and returns immediately.
     */
    void requestEvaluation(@NotNull PsiFile file);

    /**
     * Returns the global names of the {@code includes/*.js} files of the Dataform project the given
     * file belongs to, looked up through the virtual file system so it is safe on any thread.
     */
    @NotNull
    Set<String> includeNames(@Nullable VirtualFile context);

    /**
     * Drops the cached values of a single file.
     */
    void invalidate(@NotNull VirtualFile file);

    /**
     * Notes that what the expressions depend on — an include, the workflow settings, the compiled
     * schema — has changed. The cached values are kept as they are, so that a file being edited
     * keeps showing them; a run that failed before is allowed to try again at once.
     */
    void markEnvironmentChanged();

    /**
     * Drops every cached value, for changes that affect the whole evaluation environment.
     */
    void invalidateAll();
}
