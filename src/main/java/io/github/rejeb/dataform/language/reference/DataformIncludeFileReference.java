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
package io.github.rejeb.dataform.language.reference;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReferenceBase;
import io.github.rejeb.dataform.language.index.DataformJsFileIndex;
import io.github.rejeb.dataform.language.psi.SqlxFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Map;

public class DataformIncludeFileReference extends PsiReferenceBase<PsiElement> {

    private final String fileName;

    public DataformIncludeFileReference(@NotNull PsiElement element, @NotNull String fileName) {
        super(element, new TextRange(0, element.getTextLength()));
        this.fileName = fileName;
    }

    @Nullable
    @Override
    public PsiElement resolve() {

        PsiFile currentFile = myElement.getContainingFile();
        if (currentFile == null) {
            return null;
        }

        Project project = myElement.getProject();
        PsiFile topLevelFile = InjectedLanguageManager.getInstance(project)
                .getTopLevelFile(myElement);

        if (!(topLevelFile instanceof SqlxFile)) {
            return null;
        }

        Map<String, List<DataformJsFileIndex.IncludeExport>> exportsByFile =
                DataformJsFileIndex.getAllExports(project);

        List<DataformJsFileIndex.IncludeExport> exports = exportsByFile.get(fileName);

        if (exports != null && !exports.isEmpty()) {
            return exports.getFirst().sourceFile();
        }

        return null;
    }

    @Override
    public Object @NonNull [] getVariants() {
        return new Object[0];
    }
}
