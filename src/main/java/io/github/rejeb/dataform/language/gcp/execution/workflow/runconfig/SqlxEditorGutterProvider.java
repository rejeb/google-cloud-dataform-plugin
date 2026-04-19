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
import com.intellij.psi.util.PsiTreeUtil;
import io.github.rejeb.dataform.language.psi.SqlxConfigBlock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

import static io.github.rejeb.dataform.language.util.Utils.isActionFile;

public class SqlxEditorGutterProvider implements LineMarkerProvider {

    @Override
    public @Nullable LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
        if (!(element instanceof SqlxConfigBlock configBlock)) return null;
        PsiFile sqlxFile = element.getContainingFile();
        if (sqlxFile == null || sqlxFile.getVirtualFile() == null) return null;
        if (!isActionFile(sqlxFile.getVirtualFile())) return null;
        Optional<PsiElement> tagProperty = findTagsProperty(configBlock);
        if (tagProperty.isEmpty()) return null;

        PsiElement anchor = tagProperty.get();
        return new LineMarkerInfo<>(
                anchor,
                anchor.getTextRange(),
                AllIcons.Actions.Execute,
                e -> "Run " + sqlxFile.getVirtualFile().getNameWithoutExtension(),
                (mouseEvent, psiElement) -> {
                    if (sqlxFile.getVirtualFile() == null) return;
                    SqlxRunOptionsPopup.RunOptionsCallback callback = (deps, dependants, fullRefresh) ->
                            RunSqlxHelper.launchFromTags(sqlxFile.getProject(), sqlxFile.getVirtualFile(),
                                    deps, dependants, fullRefresh);
                    SqlxRunOptionsPopup.show(sqlxFile.getProject(), mouseEvent, callback);
                },
                GutterIconRenderer.Alignment.LEFT,
                () -> "Run " + sqlxFile.getName()
        );
    }

    private static Optional<PsiElement> findTagsProperty(@NotNull SqlxConfigBlock configBlock) {
        InjectedLanguageManager manager = InjectedLanguageManager.getInstance(configBlock.getProject());
        List<Pair<PsiElement, TextRange>> injected = manager.getInjectedPsiFiles(configBlock);
        if (injected == null) return Optional.empty();
        return injected.parallelStream()
                .flatMap(injectedPsi ->
                        PsiTreeUtil.findChildrenOfType(injectedPsi.getFirst(), JSProperty.class).stream())
                .filter(p -> "tags".equals(p.getName()))
                .findFirst()
                .map(PsiElement::getFirstChild);
    }
}