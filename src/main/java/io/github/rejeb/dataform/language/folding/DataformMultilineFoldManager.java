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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.editor.CustomFoldRegion;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders long expression values over several editor lines, using custom fold regions.
 *
 * <p>A custom fold region spans whole lines and cannot be expanded, so clicking the painted value
 * removes it to bring the expression source back. The next evaluation pass — run when the file is
 * opened or comes back into focus — creates it again.</p>
 */
public final class DataformMultilineFoldManager {

    private static final Logger LOG = Logger.getInstance(DataformMultilineFoldManager.class);
    private static final Key<Map<String, CustomFoldRegion>> REGIONS = Key.create("dataform.folding.multilineRegions");

    /**
     * A value to paint over the lines of one expression.
     *
     * @param source    the expression source, used to identify the fold across passes
     * @param lines     the formatted value, one entry per line
     * @param startLine first document line the expression occupies
     * @param endLine   last document line the expression occupies
     * @param prefix    the text preceding the expression on its first line, repainted as is
     * @param suffix    the text following the expression on its last line, repainted as is
     */
    public record MultilineValue(@NotNull String source,
                                 @NotNull List<String> lines,
                                 int startLine,
                                 int endLine,
                                 @NotNull String prefix,
                                 @NotNull String suffix) {
    }

    private DataformMultilineFoldManager() {
    }

    /**
     * Creates the missing multi-line regions of an editor and drops the ones that no longer apply.
     * Must be called on the EDT.
     */
    public static void apply(@NotNull Editor editor, @NotNull List<MultilineValue> values) {
        Map<String, CustomFoldRegion> regions = regionsOf(editor);
        editor.getFoldingModel().runBatchFoldingOperation(() -> {
            removeObsolete(editor, regions, values);
            for (MultilineValue value : values) {
                if (regions.containsKey(key(value))) {
                    continue;
                }
                CustomFoldRegion region = create(editor, value);
                if (region != null) {
                    regions.put(key(value), region);
                }
            }
        });
    }

    /**
     * Drops every multi-line region of an editor, for instance when the feature is switched off.
     */
    public static void clear(@NotNull Editor editor) {
        Map<String, CustomFoldRegion> regions = regionsOf(editor);
        if (regions.isEmpty()) {
            return;
        }
        editor.getFoldingModel().runBatchFoldingOperation(() -> {
            regions.values().forEach(region -> remove(editor, region));
            regions.clear();
        });
    }

    private static void removeObsolete(@NotNull Editor editor,
                                       @NotNull Map<String, CustomFoldRegion> regions,
                                       @NotNull List<MultilineValue> wanted) {
        List<String> keep = wanted.stream().map(DataformMultilineFoldManager::key).toList();
        List<String> obsolete = regions.keySet().stream()
                .filter(key -> !keep.contains(key) || !regions.get(key).isValid())
                .toList();
        for (String key : obsolete) {
            remove(editor, regions.remove(key));
        }
    }

    private static CustomFoldRegion create(@NotNull Editor editor, @NotNull MultilineValue value) {
        try {
            return editor.getFoldingModel().addCustomLinesFolding(value.startLine(), value.endLine(),
                    new DataformValueFoldRenderer(value.lines(), value.prefix(), value.suffix(),
                            () -> showSource(editor, value)));
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (Exception e) {
            LOG.warn("Could not render the value of [" + value.source() + "] over multiple lines", e);
            return null;
        }
    }

    private static void showSource(@NotNull Editor editor, @NotNull MultilineValue value) {
        Map<String, CustomFoldRegion> regions = regionsOf(editor);
        CustomFoldRegion region = regions.remove(key(value));
        if (region != null) {
            editor.getFoldingModel().runBatchFoldingOperation(() -> remove(editor, region));
        }
    }

    private static void remove(@NotNull Editor editor, FoldRegion region) {
        if (region != null && region.isValid()) {
            editor.getFoldingModel().removeFoldRegion(region);
        }
    }

    @NotNull
    private static String key(@NotNull MultilineValue value) {
        return value.startLine() + ":" + value.endLine() + ":" + value.source();
    }

    @NotNull
    private static Map<String, CustomFoldRegion> regionsOf(@NotNull Editor editor) {
        Map<String, CustomFoldRegion> regions = editor.getUserData(REGIONS);
        if (regions == null) {
            regions = new HashMap<>();
            editor.putUserData(REGIONS, regions);
        }
        return regions;
    }
}
