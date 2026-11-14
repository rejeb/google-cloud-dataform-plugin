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
package io.github.rejeb.dataform.language.lineage.view;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import io.github.rejeb.dataform.language.lineage.dataplex.DataplexLineageService;
import io.github.rejeb.dataform.language.lineage.dataplex.DataplexView;
import io.github.rejeb.dataform.language.lineage.model.LineageModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Clock;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * The freshness tooltips of the nodes of a recorded lineage. A tooltip is asked for the first time
 * its node is hovered, off the EDT, and kept until another lineage is shown. To be used on the EDT.
 */
final class FreshnessTips {

    static final String LOADING = "Loading…";

    private final LineageModel model;
    private final DataplexLineageService service;
    private final Executor executor;
    private final Clock clock;
    private final Map<String, String> texts = new HashMap<>();
    private final Set<String> asked = new HashSet<>();
    private @Nullable DataplexView shown;

    FreshnessTips(@NotNull LineageModel model, @NotNull DataplexLineageService service, @NotNull Executor executor,
                  @NotNull Clock clock) {
        this.model = model;
        this.service = service;
        this.executor = executor;
        this.clock = clock;
    }

    /**
     * @param nodeId  the hovered node
     * @param onReady run on the EDT once the tooltip of that node is known
     * @return the tooltip of the node, {@link #LOADING} while it is being read, {@code null} when
     * no recorded lineage is shown
     */
    @Nullable String text(@NotNull String nodeId, @NotNull Runnable onReady) {
        DataplexView view = model.dataplexView();
        if (view == null) return null;
        if (view != shown) {
            shown = view;
            texts.clear();
            asked.clear();
        }
        String known = texts.get(nodeId);
        if (known != null) return known;
        if (asked.add(nodeId)) {
            executor.execute(() -> {
                String text = read(view, nodeId);
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (shown != view) return;
                    texts.put(nodeId, text);
                    onReady.run();
                }, ModalityState.any());
            });
        }
        return LOADING;
    }

    private @NotNull String read(@NotNull DataplexView view, @NotNull String nodeId) {
        try {
            return FreshnessText.tooltip(service.freshness(view, nodeId), view.range(), clock);
        } catch (RuntimeException e) {
            return "Knowledge Catalog cannot answer: " + e.getMessage();
        }
    }
}
