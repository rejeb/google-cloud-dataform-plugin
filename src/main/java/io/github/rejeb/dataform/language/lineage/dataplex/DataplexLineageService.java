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
package io.github.rejeb.dataform.language.lineage.dataplex;

import io.github.rejeb.dataform.language.columns.model.ColumnRef;
import com.intellij.openapi.project.Project;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * Loads and caches the lineage Knowledge Catalog recorded around the tables of the project. Every
 * method blocks on the network and must be called off the event dispatch thread; every failure is
 * a {@link DataplexLineageException}.
 */
public interface DataplexLineageService {

    static DataplexLineageService getInstance(@NotNull Project project) {
        return project.getService(DataplexLineageService.class);
    }

    /**
     * @return the project and location the recorded lineage of the compiled project is read in
     * @throws DataplexLineageException when the project has no Google Cloud project
     */
    @NotNull LineageScope scopeOf(@NotNull CompiledGraph compiled);

    /**
     * @return the recorded lineage of the repository as it was last read for that range, or
     * {@code null} when it was never read; no network call is made
     */
    @Nullable DataplexView cachedView(@NotNull CompiledGraph compiled, @NotNull TimeRange range);

    /**
     * Tells whether the recorded lineage of the repository must be read again: it was never read,
     * the repository tables changed, or Knowledge Catalog recorded a process since. Costs at most
     * one call; callers asking together share it.
     */
    boolean needsReload(@NotNull CompiledGraph compiled, @NotNull TimeRange range);

    /**
     * Reads the recorded lineage of every repository table again, re-applies the expanders that
     * were clicked and keeps the result in the project cache. Callers asking together share one read.
     */
    @NotNull DataplexView reload(@NotNull CompiledGraph compiled, @NotNull TimeRange range);

    /**
     * Adds a table that is not on the view, with its links one level away each way.
     *
     * @return the larger view, or the given one when the table is already on it
     */
    @NotNull DataplexView include(@NotNull DataplexView view, @NotNull String nodeId);

    /**
     * Notes that a lineage view showing that scope and range is shown, so that its data are held and
     * it is given each new entry read for them, whichever view asked for it.
     *
     * @param onNewEntry receives the view of each new entry, on the thread that read it
     */
    void retain(@NotNull LineageScope scope, @NotNull TimeRange range, @NotNull Consumer<DataplexView> onNewEntry);

    /**
     * Notes that a lineage view showing that scope and range is hidden; when none is shown any more,
     * the data held for them and the answers asked about them are dropped.
     *
     * @param onNewEntry the receiver given to {@link #retain}
     */
    void release(@NotNull LineageScope scope, @NotNull TimeRange range, @NotNull Consumer<DataplexView> onNewEntry);

    /**
     * Loads what is hidden on one side of a node of the view.
     *
     * @return the larger view, or the given one when nothing is hidden there
     */
    @NotNull DataplexView expand(@NotNull DataplexView view, @NotNull String nodeId,
                                 @NotNull DataplexLineageClient.Direction direction);

    /**
     * @return when the table of the node was last written within the range of the view
     */
    @NotNull Freshness freshness(@NotNull DataplexView view, @NotNull String nodeId);

    /**
     * @return the processes and runs recorded for the flow between two nodes of the view
     */
    @NotNull LinkDetails linkDetails(@NotNull DataplexView view, @NotNull String fromNodeId, @NotNull String toNodeId);

    /**
     * Follows the recorded lineage of the table of a node one way, up to five levels, whether the
     * entities are on the view or not.
     *
     * @return the entities found, the nearest first, without the table itself and without the
     * assertions of the repository; at most {@link DataplexGraphLoader#MAX_ENTITIES}
     */
    @NotNull List<TraceEntry> trace(@NotNull DataplexView view, @NotNull String nodeId,
                                    @NotNull DataplexLineageClient.Direction direction);

    /**
     * @return the processes recorded on the loaded links of the node, the latest first
     */
    @NotNull List<ProcessUse> processesOf(@NotNull DataplexView view, @NotNull String nodeId);

    /**
     * @return the latest runs that wrote the table of the node, each with its process and what the
     * process read, newest first
     */
    @NotNull List<ProcessRun> runsWriting(@NotNull DataplexView view, @NotNull String nodeId);

    /**
     * @return the latest runs recorded for the flow between two nodes of the view, newest first;
     * none when the flow is not loaded
     */
    @NotNull List<ProcessRun> runsOfLink(@NotNull DataplexView view, @NotNull String fromNodeId, @NotNull String toNodeId);

    /**
     * Reads the column lineage Knowledge Catalog recorded around the roots, within the tables of
     * the view. It blocks on the network and must not be called on the event dispatch thread.
     *
     * @param roots the columns whose lineage is shown
     * @return the recorded column lineage, or an empty one and why it could not be read
     */
    @NotNull DataplexColumnLineage.Result columnLineage(@NotNull DataplexView view,
                                                       @NotNull List<ColumnRef> roots);
}
