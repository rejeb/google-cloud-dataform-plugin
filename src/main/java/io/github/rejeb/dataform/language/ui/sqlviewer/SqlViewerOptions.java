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

import com.intellij.openapi.fileTypes.FileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * How a {@link SqlViewer} is set up by the place that shows it. Starts from {@link #fill()} or
 * {@link #fitText(int)}, with the Copy button and line numbers shown and SQL highlighting.
 *
 * @param height      how tall the viewer is
 * @param fileType    the language the text is highlighted as, {@code null} for SQL
 * @param lineNumbers whether the line numbers are shown
 * @param copyButton  whether a Copy button is shown above the text
 * @param resize      where the height the user drags the viewer to is remembered, {@code null} when
 *                    the viewer cannot be resized
 */
public record SqlViewerOptions(@NotNull SqlViewerHeight height, @Nullable FileType fileType, boolean lineNumbers,
                               boolean copyButton, @Nullable Resize resize) {

    /**
     * Where the height of a resizable viewer is remembered.
     *
     * @param heights the heights of the viewers of one view
     * @param key     the key of the viewer, the same each time the view rebuilds it
     */
    public record Resize(@NotNull SqlViewerHeights heights, @NotNull String key) {
    }

    /**
     * A viewer taking all the height its container gives it.
     */
    public static @NotNull SqlViewerOptions fill() {
        return new SqlViewerOptions(new SqlViewerHeight.Fill(), null, true, true, null);
    }

    /**
     * A viewer as tall as its text, up to the given number of lines.
     */
    public static @NotNull SqlViewerOptions fitText(int maxLines) {
        return new SqlViewerOptions(new SqlViewerHeight.FitText(maxLines), null, true, true, null);
    }

    /**
     * The same options, highlighting the text as the given language; plain text for messages.
     */
    public @NotNull SqlViewerOptions withFileType(@Nullable FileType type) {
        return new SqlViewerOptions(height, type, lineNumbers, copyButton, resize);
    }

    /**
     * The same options, showing the line numbers or not.
     */
    public @NotNull SqlViewerOptions withLineNumbers(boolean shown) {
        return new SqlViewerOptions(height, fileType, shown, copyButton, resize);
    }

    /**
     * The same options, showing the Copy button or not.
     */
    public @NotNull SqlViewerOptions withCopyButton(boolean shown) {
        return new SqlViewerOptions(height, fileType, lineNumbers, shown, resize);
    }

    /**
     * The same options, letting the user drag the bottom edge of a fit-text viewer to resize it; the
     * height is remembered in the given heights under the given key.
     */
    public @NotNull SqlViewerOptions withResize(@NotNull SqlViewerHeights heights, @NotNull String key) {
        return new SqlViewerOptions(height, fileType, lineNumbers, copyButton, new Resize(heights, key));
    }
}
