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
package io.github.rejeb.dataform.language.gcp.execution.unittest.runconfig;

import com.intellij.execution.lineMarker.RunLineMarkerContributor;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import io.github.rejeb.dataform.language.psi.SqlxConfigBlock;
import io.github.rejeb.dataform.language.psi.SqlxConfigBlocks;
import io.github.rejeb.dataform.language.unittest.SqlxUnitTests;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DataformTestRunLineMarkerContributor extends RunLineMarkerContributor {

    @Override
    public @Nullable Info getInfo(@NotNull PsiElement element) {
        if (!(element instanceof LeafPsiElement)) {
            return null;
        }
        SqlxConfigBlock config = SqlxConfigBlocks.startingWith(element);
        PsiFile file = config == null ? null : config.getContainingFile();
        VirtualFile virtualFile = file == null ? null : file.getVirtualFile();
        if (virtualFile == null || !SqlxUnitTests.isUnitTestFile(file)) {
            return null;
        }
        VirtualFile projectDir = ProjectUtil.guessProjectDir(element.getProject());
        String path = projectDir == null ? null : VfsUtilCore.getRelativePath(virtualFile, projectDir);
        if (path == null) {
            return null;
        }
        return withExecutorActions(getTestStateIcon(DataformTestLocator.locationHint(path), element.getProject(), false));
    }
}
