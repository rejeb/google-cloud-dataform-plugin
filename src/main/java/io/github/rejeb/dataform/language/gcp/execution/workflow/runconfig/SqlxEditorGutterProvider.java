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
package io.github.rejeb.dataform.language.gcp.execution.workflow.runconfig;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.icons.AllIcons;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.javascript.psi.JSProperty;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import io.github.rejeb.dataform.language.psi.SqlxConfigBlock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static io.github.rejeb.dataform.language.util.Utils.isActionFile;

/**
 * Puts a run icon in the gutter of an action file whose {@code config} block declares tags. The
 * marker is anchored on the first leaf of the host {@code config} block, never on the injected
 * JavaScript: line markers must belong to a leaf of the file being highlighted.
 */
public class SqlxEditorGutterProvider implements LineMarkerProvider {

    @Override
    public @Nullable LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
        if (!(element instanceof LeafPsiElement)) return null;
        SqlxConfigBlock configBlock = PsiTreeUtil.getParentOfType(element, SqlxConfigBlock.class);
        if (configBlock == null || PsiTreeUtil.getDeepestFirst(configBlock) != element) return null;
        PsiFile sqlxFile = configBlock.getContainingFile();
        if (sqlxFile == null || sqlxFile.getVirtualFile() == null) return null;
        if (!isActionFile(sqlxFile.getVirtualFile())) return null;
        if (!declaresTags(configBlock)) return null;

        return new LineMarkerInfo<>(
                element,
                element.getTextRange(),
                AllIcons.Actions.Execute,
                e -> "Run " + sqlxFile.getVirtualFile().getNameWithoutExtension(),
                (mouseEvent, psiElement) -> {
                    if (sqlxFile.getVirtualFile() == null) return;
                    RunSqlxHelper.launchFromTags(sqlxFile.getProject(), sqlxFile.getVirtualFile());
                },
                GutterIconRenderer.Alignment.LEFT,
                () -> "Run " + sqlxFile.getName()
        );
    }

    private static boolean declaresTags(@NotNull SqlxConfigBlock configBlock) {
        InjectedLanguageManager manager = InjectedLanguageManager.getInstance(configBlock.getProject());
        List<Pair<PsiElement, TextRange>> injected = manager.getInjectedPsiFiles(configBlock);
        if (injected == null) return false;
        for (Pair<PsiElement, TextRange> pair : injected) {
            for (JSProperty property : PsiTreeUtil.findChildrenOfType(pair.getFirst(), JSProperty.class)) {
                if ("tags".equals(property.getName())) return true;
            }
        }
        return false;
    }
}
