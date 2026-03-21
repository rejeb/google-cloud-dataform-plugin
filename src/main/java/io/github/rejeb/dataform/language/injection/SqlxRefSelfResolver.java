/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
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
package io.github.rejeb.dataform.language.injection;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import io.github.rejeb.dataform.language.compilation.DataformCompilationService;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.compilation.model.Target;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class SqlxRefSelfResolver {

    private static final Pattern REF_PATTERN = Pattern.compile(
            "\\$\\{\\s*ref\\s*\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)\\s*\\}"
    );

    private static final Pattern SELF_PATTERN = Pattern.compile(
            "\\$\\{\\s*self\\s*\\(\\s*\\)\\s*\\}"
    );

    private SqlxRefSelfResolver() {
    }

    @Nullable
    public static String resolveToSqlIdentifier(@NotNull PsiElement element,
                                                @Nullable String currentFileName) {
        String text = element.getText();
        if (text == null || text.isBlank()) return null;

        Project project = element.getProject();
        DataformCompilationService compilationService =
                project.getService(DataformCompilationService.class);
        if (compilationService == null) return null;

        CompiledGraph graph = compilationService.getCompiledGraph();
        if (graph == null) return null;

        Matcher refMatcher = REF_PATTERN.matcher(text);
        if (refMatcher.matches()) {
            String refName = refMatcher.group(1);
            return graph.findTargetByRefName(refName)
                    .map(SqlxRefSelfResolver::toBigQueryIdentifier)
                    .orElse(null);
        }

        if (SELF_PATTERN.matcher(text).matches() && currentFileName != null) {
            return graph.findTargetByRefName(currentFileName)
                    .map(SqlxRefSelfResolver::toBigQueryIdentifier)
                    .orElse(null);
        }

        return null;
    }

    @NotNull
    private static String toBigQueryIdentifier(@NotNull Target target) {
        return Stream.of("gcdp",target.getDatabase(), target.getSchema(), target.getName())
                .filter(part -> part != null && !part.isBlank())
                .map(SqlxRefSelfResolver::quoteIfNeeded)
                .collect(Collectors.joining("."));
    }

    private static String quoteIfNeeded(@NotNull String part) {
        return part.matches("[a-zA-Z_][a-zA-Z0-9_]*") ? part : "`" + part + "`";
    }
}
