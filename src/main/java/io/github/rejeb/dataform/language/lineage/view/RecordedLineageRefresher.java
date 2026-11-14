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
import com.intellij.openapi.progress.ProcessCanceledException;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.lineage.dataplex.DataplexLineageException;
import io.github.rejeb.dataform.language.lineage.dataplex.DataplexLineageService;
import io.github.rejeb.dataform.language.lineage.dataplex.DataplexView;
import io.github.rejeb.dataform.language.lineage.dataplex.LineageScope;
import io.github.rejeb.dataform.language.lineage.dataplex.TimeRange;
import io.github.rejeb.dataform.language.lineage.model.RecordedActivity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Brings the recorded lineage to a lineage view while it is shown: the cached lineage at once, then
 * the one-call check, then a reload in the background when something new was recorded. A new entry
 * read for another view of the same scope and range is shown as an update too. Answers that come
 * after the view was hidden or asked again are dropped.
 */
final class RecordedLineageRefresher {

    /** Receives what the refresher brings, on the event dispatch thread. */
    interface Listener {

        void onView(@NotNull DataplexView view, boolean update);

        void onActivity(@NotNull RecordedActivity activity);

        void onFailure(@NotNull DataplexLineageException.Kind kind, @Nullable String detail, boolean viewShown);
    }

    private final DataplexLineageService service;
    private final Executor executor;
    private final Executor ui;
    private final Listener listener;
    private final Object lock = new Object();
    private int generation;
    private @Nullable Retained retained;
    private boolean delivered;
    private @Nullable DataplexView announced;
    private final Consumer<DataplexView> onNewEntry = this::onNewEntry;

    RecordedLineageRefresher(@NotNull DataplexLineageService service, @NotNull Executor executor,
                             @NotNull Listener listener) {
        this(service, executor, task -> ApplicationManager.getApplication().invokeLater(task, ModalityState.nonModal()),
                listener);
    }

    RecordedLineageRefresher(@NotNull DataplexLineageService service, @NotNull Executor executor, @NotNull Executor ui,
                             @NotNull Listener listener) {
        this.service = service;
        this.executor = executor;
        this.ui = ui;
        this.listener = listener;
    }

    /**
     * The view is shown, or shows another range: draws the cached lineage, checks it and updates it,
     * or loads it when nothing is cached.
     */
    void show(@NotNull CompiledGraph compiled, @NotNull TimeRange range) {
        int asked = next();
        delivered = false;
        announced = null;
        executor.execute(() -> refresh(asked, compiled, range, false, false));
    }

    /**
     * Reads the recorded lineage again without the check.
     */
    void reload(@NotNull CompiledGraph compiled, @NotNull TimeRange range) {
        int asked = next();
        boolean shown = delivered;
        executor.execute(() -> refresh(asked, compiled, range, true, shown));
    }

    /**
     * The view is hidden: pending answers are dropped and the lineage it held is released.
     */
    void hide() {
        synchronized (lock) {
            generation++;
            if (retained != null) {
                service.release(retained.scope(), retained.range(), onNewEntry);
                retained = null;
            }
        }
        delivered = false;
        announced = null;
    }

    private int next() {
        synchronized (lock) {
            return ++generation;
        }
    }

    private boolean isCurrent(int asked) {
        synchronized (lock) {
            return asked == generation;
        }
    }

    private void refresh(int asked, @NotNull CompiledGraph compiled, @NotNull TimeRange range, boolean forced,
                         boolean shown) {
        boolean viewShown = shown;
        try {
            if (!retain(asked, service.scopeOf(compiled), range)) return;
            if (!forced) {
                DataplexView cached = service.cachedView(compiled, range);
                if (cached != null) {
                    deliverView(asked, cached, false);
                    viewShown = true;
                    activity(asked, RecordedActivity.CHECKING);
                    if (!needsReload(compiled, range)) return;
                }
            }
            if (!isCurrent(asked)) return;
            activity(asked, viewShown ? RecordedActivity.UPDATING : RecordedActivity.LOADING);
            deliverView(asked, service.reload(compiled, range), viewShown);
        } catch (ProcessCanceledException e) {
            return;
        } catch (DataplexLineageException e) {
            failure(asked, e.kind(), e.getMessage(), viewShown);
        } catch (RuntimeException e) {
            failure(asked, DataplexLineageException.Kind.OTHER, e.getMessage(), viewShown);
        } finally {
            activity(asked, RecordedActivity.NONE);
        }
    }

    private boolean needsReload(@NotNull CompiledGraph compiled, @NotNull TimeRange range) {
        try {
            return service.needsReload(compiled, range);
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private boolean retain(int asked, @NotNull LineageScope scope, @NotNull TimeRange range) {
        synchronized (lock) {
            if (asked != generation) return false;
            Retained wanted = new Retained(scope, range);
            if (!wanted.equals(retained)) {
                if (retained != null) service.release(retained.scope(), retained.range(), onNewEntry);
                service.retain(scope, range, onNewEntry);
                retained = wanted;
            }
            return true;
        }
    }

    private void onNewEntry(@NotNull DataplexView view) {
        ui.execute(() -> {
            if (!delivered || !isRetained(view)) return;
            announced = view;
            listener.onView(view, true);
        });
    }

    private boolean isRetained(@NotNull DataplexView view) {
        synchronized (lock) {
            return new Retained(view.scope(), view.range()).equals(retained);
        }
    }

    private void deliverView(int asked, @NotNull DataplexView view, boolean update) {
        ui.execute(() -> {
            if (!isCurrent(asked)) return;
            delivered = true;
            if (view != announced) listener.onView(view, update);
        });
    }

    private void activity(int asked, @NotNull RecordedActivity activity) {
        ui.execute(() -> {
            if (isCurrent(asked)) listener.onActivity(activity);
        });
    }

    private void failure(int asked, @NotNull DataplexLineageException.Kind kind, @Nullable String detail, boolean viewShown) {
        ui.execute(() -> {
            if (isCurrent(asked)) listener.onFailure(kind, detail, viewShown);
        });
    }

    private record Retained(@NotNull LineageScope scope, @NotNull TimeRange range) {
    }
}
