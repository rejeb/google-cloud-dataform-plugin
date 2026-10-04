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
package io.github.rejeb.dataform.language.unittest.execution.runconfig;

import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.testframework.sm.runner.SMTestLocator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import io.github.rejeb.dataform.language.util.DataformPaths;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class DataformTestLocator implements SMTestLocator {

    public static final String PROTOCOL = "dataform-test";
    public static final DataformTestLocator INSTANCE = new DataformTestLocator();

    private DataformTestLocator() {
    }

    /**
     * Returns the location URL of a test file, also used as the key of its gutter state.
     */
    public static @NotNull String locationHint(@NotNull String projectRelativePath) {
        return PROTOCOL + "://" + DataformPaths.normalize(projectRelativePath);
    }

    @Override
    public @NotNull List<Location> getLocation(@NotNull String protocol, @NotNull String path,
                                               @NotNull Project project, @NotNull GlobalSearchScope scope) {
        if (!PROTOCOL.equals(protocol)) {
            return List.of();
        }
        PsiFile psiFile = DataformPaths.findPsiFileInProject(project, path);
        return psiFile == null ? List.of() : List.of(PsiLocation.fromPsiElement(psiFile));
    }
}
