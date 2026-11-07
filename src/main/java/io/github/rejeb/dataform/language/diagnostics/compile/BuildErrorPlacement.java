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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.LineColumn;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import io.github.rejeb.dataform.language.compilation.PlacedError;
import io.github.rejeb.dataform.language.compilation.model.CompilationError;
import io.github.rejeb.dataform.language.diagnostics.sql.hint.SqlHint;
import io.github.rejeb.dataform.language.util.DataformPaths;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Places a compilation error of the Build tool window in the project as it reads now, the way the
 * editor places it: in the file it is reported for, or else in the include of the project it is
 * thrown in.
 */
public final class BuildErrorPlacement {

    private static final List<String> PROJECT_FOLDERS = List.of("definitions/", "includes/");

    private BuildErrorPlacement() {
    }

    /**
     * Places an error. Must be called inside a read action.
     *
     * @return where the error is, or {@code null} when its file is missing or what it names is no
     * longer written there
     */
    public static @Nullable PlacedError place(@NotNull Project project, @NotNull CompilationError error) {
        String fileName = DataformPaths.normalize(error.getFileName());
        PsiFile file = DataformPaths.findPsiFileInProject(project, fileName);
        if (fileName == null || file == null || file.getVirtualFile() == null) return null;
        ParsedCompilationError parsed = CompilationErrorParser.parse(error.getMessage(), error.getStack(), fileName);
        String path = file.getVirtualFile().getPath();
        TextRange range = CompilationErrorPlacer.place(file, path, parsed);
        if (range != null) return placed(fileName, file, path, range, parsed, null);
        StackFrame thrownIn = includeFrame(parsed, fileName);
        if (thrownIn == null) return null;
        PsiFile include = DataformPaths.findPsiFileInProject(project, thrownIn.path());
        if (include == null || include.getVirtualFile() == null) return null;
        String includePath = include.getVirtualFile().getPath();
        TextRange raised = CompilationErrorPlacer.placeRaised(include, includePath, parsed);
        return raised == null ? null
                : placed(fileName, include, includePath, raised, parsed, DataformPaths.normalize(thrownIn.path()));
    }

    private static @Nullable StackFrame includeFrame(@NotNull ParsedCompilationError parsed, @NotNull String fileName) {
        for (StackFrame frame : parsed.frames()) {
            String path = DataformPaths.normalize(frame.path());
            if (PROJECT_FOLDERS.stream().anyMatch(path::startsWith) && !DataformPaths.pointsTo(fileName, path)) {
                return frame;
            }
        }
        return null;
    }

    private static @NotNull PlacedError placed(@NotNull String fileName, @NotNull PsiFile target, @NotNull String targetPath,
                                               @NotNull TextRange range, @NotNull ParsedCompilationError parsed,
                                               @Nullable String thrownInPath) {
        String text = target.getText();
        LineColumn at = StringUtil.offsetToLineColumn(text, range.getStartOffset());
        SqlHint hint = CompilationHints.hintAt(target, targetPath, parsed, range);
        return new PlacedError(fileName, at.line, at.column, range.getLength(), text, hint.text(), thrownInPath);
    }
}
