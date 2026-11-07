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
package io.github.rejeb.dataform.language.ui.sqlviewer;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * The heights the user gave to resizable viewers, by the key of each viewer. The view that owns the
 * viewers keeps one instance for as long as it is open, so a viewer rebuilt with the same key comes
 * back at the height the user chose.
 */
public final class SqlViewerHeights {

    private final Map<String, Integer> heights = new HashMap<>();

    /**
     * The height the user gave to the viewer of the given key, {@code null} when it was not resized.
     */
    public @Nullable Integer get(@NotNull String key) {
        return heights.get(key);
    }

    /**
     * Remembers the height the user gave to the viewer of the given key.
     */
    public void remember(@NotNull String key, int height) {
        heights.put(key, height);
    }

    /**
     * Forgets the height of the viewer of the given key, which goes back to its automatic height.
     */
    public void forget(@NotNull String key) {
        heights.remove(key);
    }
}
