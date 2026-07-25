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
package io.github.rejeb.dataform.language.folding;

import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.FoldingGroup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Shared presentation rules for folded Dataform expression values.
 */
public final class DataformFoldingPlaceholder {

    public static final String GROUP_NAME = "dataform.expression.value";

    private static final int MAX_LENGTH = 100;
    private static final String EMPTY_VALUE = "''";
    private static final String ELLIPSIS = "…";

    private DataformFoldingPlaceholder() {
    }

    /**
     * Creates a group for a single fold region.
     *
     * <p>Regions sharing a group instance expand and collapse together, so every region gets its
     * own instance. The shared debug name is what identifies the region as ours afterwards.</p>
     */
    @NotNull
    public static FoldingGroup newGroup() {
        return FoldingGroup.newGroup(GROUP_NAME);
    }

    /**
     * Tells whether the given fold region was created for a Dataform expression value.
     */
    public static boolean isDataformRegion(@Nullable FoldingGroup group) {
        return group != null && GROUP_NAME.equals(group.toString());
    }

    /**
     * Builds the placeholder shown in place of the expression, or {@code null} when the value adds
     * nothing over the source text.
     */
    @Nullable
    public static String of(@NotNull String value, @NotNull String hostText) {
        String collapsed = compact(value);
        if (collapsed.equals(hostText.trim())) {
            return null;
        }
        if (collapsed.isEmpty()) {
            return EMPTY_VALUE;
        }
        return collapsed.length() <= MAX_LENGTH ? collapsed : collapsed.substring(0, MAX_LENGTH) + ELLIPSIS;
    }

    /**
     * Renders the value the way it should read in a tooltip: original line breaks kept, indentation
     * normalised, and no truncation.
     */
    @NotNull
    public static String expanded(@NotNull String value) {
        List<String> lines = new ArrayList<>(value.replace("\t", "    ").lines()
                .map(String::stripTrailing)
                .toList());
        while (!lines.isEmpty() && lines.getFirst().isBlank()) {
            lines.removeFirst();
        }
        while (!lines.isEmpty() && lines.getLast().isBlank()) {
            lines.removeLast();
        }

        int commonIndent = lines.stream()
                .filter(line -> !line.isBlank())
                .mapToInt(DataformFoldingPlaceholder::indentOf)
                .min()
                .orElse(0);
        return lines.stream()
                .map(line -> line.isBlank() ? "" : line.substring(commonIndent))
                .collect(Collectors.joining("\n"));
    }

    @NotNull
    private static String compact(@NotNull String value) {
        return value.replaceAll("\\s+", " ").trim();
    }

    private static int indentOf(@NotNull String line) {
        int indent = 0;
        while (indent < line.length() && line.charAt(indent) == ' ') {
            indent++;
        }
        return indent;
    }

    /**
     * Returns an empty descriptor array, for the paths where folding must not appear.
     */
    public static FoldingDescriptor @NotNull [] none() {
        return FoldingDescriptor.EMPTY_ARRAY;
    }
}
