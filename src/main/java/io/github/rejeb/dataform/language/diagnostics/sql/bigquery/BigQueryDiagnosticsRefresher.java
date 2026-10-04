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
package io.github.rejeb.dataform.language.diagnostics.sql.bigquery;

import com.intellij.openapi.project.Project;
import io.github.rejeb.dataform.language.diagnostics.DataformEditorRefresher;
import io.github.rejeb.dataform.language.schema.sql.DataformSchemaEvent;
import io.github.rejeb.dataform.language.schema.sql.DryRunErrorRegistry;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Repaints the editors once a schema extraction has changed the recorded dry-run failures, so the
 * BigQuery errors appear, move or disappear without waiting for the next edit.
 */
public final class BigQueryDiagnosticsRefresher implements DataformSchemaEvent {

    private final Project project;
    private final AtomicLong refreshedAt = new AtomicLong(-1);

    public BigQueryDiagnosticsRefresher(@NotNull Project project) {
        this.project = project;
    }

    @Override
    public void onSchemasUpdated() {
        if (project.isDisposed()) return;
        if (!isNewState(DryRunErrorRegistry.getInstance(project).getModificationCount())) return;
        DataformEditorRefresher.refreshWithInlays(project);
    }

    boolean isNewState(long modificationCount) {
        return refreshedAt.getAndSet(modificationCount) != modificationCount;
    }
}
