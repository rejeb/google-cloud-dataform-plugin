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
package io.github.rejeb.dataform.language.lineage.toolwindow;

import io.github.rejeb.dataform.language.lineage.dataplex.DataplexLineageException;
import io.github.rejeb.dataform.language.lineage.model.LineageModel;
import io.github.rejeb.dataform.language.lineage.model.LineageSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * What a tab of the Dataform Lineage tool window can show.
 */
public enum LineageToolWindowState {
    /** No lineage editor is open. */
    NO_EDITOR,
    /** The lineage editor shows the Dataform source. */
    DATAFORM_SOURCE,
    /** Knowledge Catalog refused or failed to answer. */
    CANNOT_ANSWER,
    /** Nothing is selected on the recorded lineage. */
    NO_SELECTION,
    /** A table or a link of the recorded lineage is selected. */
    READY;

    /**
     * @param model   the model of the lineage editor the tool window follows, {@code null} without one
     * @param failure why the last request of the tab failed, {@code null} when it did not
     * @return what the tab can show
     */
    public static @NotNull LineageToolWindowState of(@Nullable LineageModel model,
                                                     @Nullable DataplexLineageException.Kind failure) {
        if (model == null) return NO_EDITOR;
        if (model.source() != LineageSource.DATAPLEX) return DATAFORM_SOURCE;
        if (failure != null) return CANNOT_ANSWER;
        boolean selected = model.selectedId() != null || model.selectedLink() != null;
        return model.dataplexView() != null && selected ? READY : NO_SELECTION;
    }

    /**
     * @return the sentence telling why Knowledge Catalog gave no answer
     */
    public static @NotNull String cannotAnswer(@NotNull RuntimeException failure) {
        return "Knowledge Catalog cannot answer: " + reasonOf(failure);
    }

    /**
     * @return the kind of a failed request, {@link DataplexLineageException.Kind#OTHER} for a failure
     * that does not come from Knowledge Catalog
     */
    public static @NotNull DataplexLineageException.Kind kindOf(@NotNull RuntimeException failure) {
        return failure instanceof DataplexLineageException known ? known.kind() : DataplexLineageException.Kind.OTHER;
    }

    private static @NotNull String reasonOf(@NotNull RuntimeException failure) {
        return switch (kindOf(failure)) {
            case API_DISABLED -> "the Data Lineage API is not enabled";
            case PERMISSION_DENIED -> "your account cannot read the lineage";
            case NOT_SIGNED_IN -> "you are not signed in";
            case OTHER -> failure.getMessage() == null || failure.getMessage().isBlank()
                    ? "the request failed" : failure.getMessage();
        };
    }
}
