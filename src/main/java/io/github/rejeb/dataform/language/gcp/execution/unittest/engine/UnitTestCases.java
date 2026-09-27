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
package io.github.rejeb.dataform.language.gcp.execution.unittest.engine;

import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import io.github.rejeb.dataform.language.compilation.model.CompilationError;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.compilation.model.CompiledTest;
import io.github.rejeb.dataform.language.compilation.model.GraphErrors;
import io.github.rejeb.dataform.language.util.DataformPaths;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class UnitTestCases {

    private UnitTestCases() {
    }

    /**
     * Returns the tests of the graph within the scope. A file whose test did not compile yields one
     * failed case carrying the compile error.
     */
    @NotNull
    public static List<UnitTestCase> select(@NotNull CompiledGraph graph,
                                            @NotNull DataformTestScope scope,
                                            @NotNull String projectRelativePath) {
        List<UnitTestCase> cases = new ArrayList<>();
        for (CompiledTest test : graph.getTests()) {
            if (selects(scope, projectRelativePath, test.getFileName())) {
                cases.add(new UnitTestCase(test.getName(), test.getFileName(), test.getTestQuery(),
                        test.getExpectedOutputQuery(), test.isDisabled(), compilationError(graph, test.getFileName())));
            }
        }
        if (cases.isEmpty() && scope == DataformTestScope.FILE) {
            String error = compilationError(graph, projectRelativePath);
            if (error != null) {
                String fileName = trim(projectRelativePath);
                cases.add(new UnitTestCase(FileUtilRt.getNameWithoutExtension(StringUtil.substringAfterLast("/" + fileName, "/")),
                        fileName, "", "", false, error));
            }
        }
        return cases;
    }

    /**
     * Tells whether a test file lies within the scope. Both paths are project-relative and may use
     * either separator.
     */
    public static boolean selects(@NotNull DataformTestScope scope,
                                  @NotNull String projectRelativePath,
                                  @NotNull String testFileName) {
        String target = trim(projectRelativePath);
        String file = trim(testFileName);
        return switch (scope) {
            case ALL -> true;
            case FILE -> file.equals(target);
            case DIRECTORY -> target.isEmpty() || file.startsWith(target + "/");
        };
    }

    @Nullable
    private static String compilationError(@NotNull CompiledGraph graph, @NotNull String fileName) {
        GraphErrors errors = graph.getGraphErrors();
        if (errors == null || errors.getCompilationErrors() == null) {
            return null;
        }
        List<String> messages = errors.getCompilationErrors().stream()
                .filter(error -> DataformPaths.pointsTo(fileName, error.getFileName())
                        || DataformPaths.pointsTo(error.getFileName(), fileName))
                .map(CompilationError::getMessage)
                .filter(Objects::nonNull)
                .toList();
        return messages.isEmpty() ? null : String.join("\n", messages);
    }

    @NotNull
    private static String trim(@NotNull String path) {
        return StringUtil.trimEnd(StringUtil.trimLeading(DataformPaths.normalize(path), '/'), "/");
    }
}
