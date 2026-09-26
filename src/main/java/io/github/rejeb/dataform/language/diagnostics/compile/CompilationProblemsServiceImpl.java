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

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import io.github.rejeb.dataform.language.diagnostics.CompilationDiagnostic;
import io.github.rejeb.dataform.language.diagnostics.CompilationDiagnosticService;
import io.github.rejeb.dataform.language.diagnostics.sql.hint.SqlHint;
import io.github.rejeb.dataform.language.psi.SqlxFile;
import io.github.rejeb.dataform.language.util.DataformProjectLayout;
import io.github.rejeb.dataform.language.validation.SqlxValidationProblem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Default {@link CompilationProblemsService}. What it computes depends on the file and on the last
 * compilation, so it is cached against both. Errors are placed only in the files the editor shows
 * problems in, SQLX files and Dataform scripts: in any other file an error placed would be shown
 * nowhere, so it is left for the banner to list.
 */
public final class CompilationProblemsServiceImpl implements CompilationProblemsService {

    static final String PREFIX = "Dataform: ";

    private final Project project;

    public CompilationProblemsServiceImpl(@NotNull Project project) {
        this.project = project;
    }

    @Override
    public @NotNull CompilationProblems diagnose(@NotNull PsiFile file) {
        if (InjectedLanguageManager.getInstance(project).isInjectedFragment(file)) return CompilationProblems.NONE;
        VirtualFile virtualFile = file.getOriginalFile().getVirtualFile();
        if (virtualFile == null) return CompilationProblems.NONE;
        return CachedValuesManager.getCachedValue(file, () -> CachedValueProvider.Result.create(
                compute(file, virtualFile),
                PsiModificationTracker.MODIFICATION_COUNT,
                CompilationDiagnosticService.getInstance(project)));
    }

    private @NotNull CompilationProblems compute(@NotNull PsiFile file, @NotNull VirtualFile virtualFile) {
        CompilationDiagnosticService diagnostics = CompilationDiagnosticService.getInstance(project);
        String path = virtualFile.getPath();
        List<SqlxValidationProblem> located = new ArrayList<>();
        List<String> unlocated = new ArrayList<>();
        Set<TextRange> taken = new HashSet<>();
        boolean shown = file instanceof SqlxFile || DataformProjectLayout.isDataformScript(virtualFile);
        for (CompilationDiagnostic diagnostic : diagnostics.getDiagnostics(virtualFile)) {
            ParsedCompilationError error = CompilationErrorParser.parse(
                    diagnostic.message(), diagnostic.stack(), diagnostic.reportedFileName());
            TextRange range = shown ? CompilationErrorPlacer.place(file, path, error) : null;
            if (range == null) {
                unlocated.add(diagnostic.message());
            } else if (taken.add(range)) {
                located.add(problemOf(file, path, error, range, null));
            }
        }
        if (shown && !(file instanceof SqlxFile)) {
            for (CompilationDiagnostic diagnostic : diagnostics.getDiagnosticsRaisedIn(virtualFile)) {
                ParsedCompilationError error = CompilationErrorParser.parse(
                        diagnostic.message(), diagnostic.stack(), diagnostic.reportedFileName());
                TextRange range = CompilationErrorPlacer.placeRaised(file, path, error);
                if (range != null && taken.add(range)) {
                    located.add(problemOf(file, path, error, range, diagnostic.reportedFileName()));
                }
            }
        }
        if (located.isEmpty() && unlocated.isEmpty()) return CompilationProblems.NONE;
        return new CompilationProblems(List.copyOf(located), List.copyOf(unlocated));
    }

    private static @NotNull SqlxValidationProblem problemOf(@NotNull PsiFile file, @NotNull String path,
                                                            @NotNull ParsedCompilationError error,
                                                            @NotNull TextRange range,
                                                            @Nullable String compiledFile) {
        StackFrame origin = raisedElsewhere(error, path);
        StringBuilder message = new StringBuilder(PREFIX).append(error.message());
        if (origin != null) message.append(" (raised in ").append(origin.path()).append(':').append(origin.line()).append(')');
        if (compiledFile != null) message.append(" (while compiling ").append(compiledFile).append(')');
        SqlHint hint = origin != null
                ? new SqlHint("Fix it in " + origin.path() + " line " + origin.line() + ".", List.of())
                : CompilationHints.hintFor(error, range, file.getText(), PsiCompilationScope.at(file, range.getStartOffset()));
        return SqlxValidationProblem.compilationError(range, message.toString(), hint.text(), hint.fixes());
    }

    private static @Nullable StackFrame raisedElsewhere(@NotNull ParsedCompilationError error, @NotNull String path) {
        if (error.indexOfFrameIn(path) <= 0) return null;
        StackFrame top = error.frames().getFirst();
        return top.path().startsWith("definitions/") || top.path().startsWith("includes/") ? top : null;
    }
}
