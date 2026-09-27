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
package io.github.rejeb.dataform.language.fileEditor;

import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.Set;

public enum SqlxPreviewMode {
    ACTION(EnumSet.of(SqlxCompiledPreviewEditor.View.LINEAGE, SqlxCompiledPreviewEditor.View.QUERY,
            SqlxCompiledPreviewEditor.View.SCHEMA), SqlxCompiledPreviewEditor.View.LINEAGE),
    UNIT_TEST(EnumSet.of(SqlxCompiledPreviewEditor.View.TEST), SqlxCompiledPreviewEditor.View.TEST);

    private final Set<SqlxCompiledPreviewEditor.View> views;
    private final SqlxCompiledPreviewEditor.View defaultView;

    SqlxPreviewMode(@NotNull Set<SqlxCompiledPreviewEditor.View> views,
                    @NotNull SqlxCompiledPreviewEditor.View defaultView) {
        this.views = views;
        this.defaultView = defaultView;
    }

    /**
     * Tells whether the preview offers the given view in this mode.
     */
    public boolean shows(@NotNull SqlxCompiledPreviewEditor.View view) {
        return views.contains(view);
    }

    /**
     * Returns the view shown when the current one is not offered in this mode.
     */
    @NotNull
    public SqlxCompiledPreviewEditor.View defaultView() {
        return defaultView;
    }
}
