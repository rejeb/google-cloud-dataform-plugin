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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.xmlb.annotations.Tag;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public interface DataformCompilationService extends Disposable, PersistentStateComponent<DataformCompilationService.State>,
        ModificationTracker {

    static DataformCompilationService getInstance(Project project) {
        return project.getService(DataformCompilationService.class);
    }

    CompiledGraph compile(boolean forceRefresh);

    CompiledGraph getCompiledGraph();

    /**
     * Records that a Dataform source changed, whether in an editor, by a save or on disk, so the
     * current graph no longer matches the sources.
     */
    default void noteSourceChanged() {
    }

    /**
     * Whether the current graph was compiled from the sources as they are now.
     */
    default boolean isUpToDate() {
        return getCompiledGraph() != null;
    }

    /**
     * The graph compiled from the sources as they are now: at once when the current graph is up to
     * date, otherwise once the running or a new compilation has finished. Never wait on the result
     * on the EDT.
     */
    @NotNull
    default CompletableFuture<CompiledGraph> upToDateGraph() {
        if (isUpToDate()) {
            return CompletableFuture.completedFuture(getCompiledGraph());
        }
        return CompletableFuture.supplyAsync(() -> compile(false), AppExecutorUtil.getAppExecutorService());
    }

    /**
     * Moves each time the compiled graph is replaced or its errors change, whoever compiled.
     */
    @Override
    long getModificationCount();

    class State {
        @Tag("compiledGraphJson")
        public String compiledGraphJson = null;

        @Tag("lastCompileTimestamp")
        public long lastCompileTimestamp = -1;
    }
}

