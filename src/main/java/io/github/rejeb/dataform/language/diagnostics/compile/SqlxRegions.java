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
package io.github.rejeb.dataform.language.diagnostics.compile;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import io.github.rejeb.dataform.language.injection.InjectionHelper;
import io.github.rejeb.dataform.language.psi.SharedTokenTypes;
import io.github.rejeb.dataform.language.psi.SqlxConfigBlock;
import io.github.rejeb.dataform.language.psi.SqlxJsBlock;
import io.github.rejeb.dataform.language.psi.SqlxSqlBlock;
import io.github.rejeb.dataform.language.util.DataformPaths;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Where the JavaScript of a SQLX file is: its config block, its js blocks and the {@code ${…}}
 * holes of each section. The compiler runs the js blocks and then the holes of a section each time
 * it builds that section, so this is also the order an error of a section is looked for in. The
 * config block runs once, first and apart, when the file is loaded.
 */
final class SqlxRegions {

    enum Section {
        CONFIG,
        MAIN,
        PRE_OPERATIONS,
        POST_OPERATIONS
    }

    private static final String CONTEXTABLE = "contextable";

    private final @Nullable TextRange config;
    private final List<TextRange> jsBlocks;
    private final Map<Section, List<TextRange>> holes;

    private SqlxRegions(@Nullable TextRange config, @NotNull List<TextRange> jsBlocks,
                        @NotNull Map<Section, List<TextRange>> holes) {
        this.config = config;
        this.jsBlocks = jsBlocks;
        this.holes = holes;
    }

    static @NotNull SqlxRegions of(@NotNull PsiFile file) {
        SqlxConfigBlock configBlock = PsiTreeUtil.findChildOfType(file, SqlxConfigBlock.class);
        List<TextRange> jsBlocks = PsiTreeUtil.findChildrenOfType(file, SqlxJsBlock.class).stream()
                .map(PsiElement::getTextRange)
                .sorted(Comparator.comparingInt(TextRange::getStartOffset))
                .toList();
        Map<Section, List<TextRange>> holes = new EnumMap<>(Section.class);
        for (SqlxSqlBlock block : PsiTreeUtil.findChildrenOfType(file, SqlxSqlBlock.class)) {
            holes.computeIfAbsent(sectionOfBlock(block.getNode().getElementType()), section -> new ArrayList<>())
                    .addAll(InjectionHelper.collectJsElements(block, 0).keySet());
        }
        holes.values().forEach(ranges -> ranges.sort(Comparator.comparingInt(TextRange::getStartOffset)));
        return new SqlxRegions(configBlock == null ? null : configBlock.getTextRange(), jsBlocks, holes);
    }

    /**
     * The section an error was raised in, told by the frames of the file in its stack. The compiler
     * builds each section in a function of its own, named after the section; an error none of them
     * was running was raised as the file loaded, which is when the config block runs.
     */
    static @NotNull Section sectionOf(@NotNull ParsedCompilationError error, @NotNull String filePath) {
        boolean inFile = false;
        for (StackFrame frame : error.frames()) {
            if (!DataformPaths.pointsTo(filePath, frame.path())) continue;
            inFile = true;
            String function = frame.function() == null ? "" : frame.function().toLowerCase(Locale.ROOT);
            if (!function.contains(CONTEXTABLE)) continue;
            if (function.contains("preoperations")) return Section.PRE_OPERATIONS;
            if (function.contains("postoperations")) return Section.POST_OPERATIONS;
            return Section.MAIN;
        }
        return inFile ? Section.CONFIG : Section.MAIN;
    }

    @Nullable TextRange config() {
        return config;
    }

    @NotNull List<TextRange> evaluationOrder(@NotNull Section section) {
        if (section == Section.CONFIG) return config == null ? List.of() : List.of(config);
        List<TextRange> ranges = new ArrayList<>(jsBlocks);
        ranges.addAll(holes.getOrDefault(section, List.of()));
        return ranges;
    }

    @NotNull List<TextRange> allJavaScript() {
        List<TextRange> ranges = new ArrayList<>();
        if (config != null) ranges.add(config);
        ranges.addAll(jsBlocks);
        holes.values().forEach(ranges::addAll);
        ranges.sort(Comparator.comparingInt(TextRange::getStartOffset));
        return ranges;
    }

    private static @NotNull Section sectionOfBlock(@NotNull IElementType type) {
        if (type == SharedTokenTypes.PRE_OPERATIONS_CONTENT) return Section.PRE_OPERATIONS;
        if (type == SharedTokenTypes.POST_OPERATIONS_CONTENT) return Section.POST_OPERATIONS;
        return Section.MAIN;
    }
}
