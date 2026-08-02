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
package io.github.rejeb.dataform.language.util;

import com.intellij.injected.editor.DocumentWindow;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NotNull;

/**
 * Crosses the boundary between an injected fragment and the SQLX file hosting it. A SQLX file
 * injects SQL, JavaScript and JSON, so code insight often runs against an editor over the injected
 * copy while offsets, inlays and the caret belong to the host.
 */
public final class SqlxEditors {

    private SqlxEditors() {
    }

    /**
     * The editor over the SQLX file itself, unwrapping an injected one.
     */
    @NotNull
    public static Editor host(@NotNull Editor editor) {
        return editor instanceof EditorWindow window ? window.getDelegate() : editor;
    }

    /**
     * Whether the editor is the host one rather than a view over an injected fragment.
     */
    public static boolean isHost(@NotNull Editor editor) {
        return !(editor instanceof EditorWindow) && !(editor.getDocument() instanceof DocumentWindow);
    }
}
