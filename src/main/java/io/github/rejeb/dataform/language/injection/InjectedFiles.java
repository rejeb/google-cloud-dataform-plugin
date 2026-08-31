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
package io.github.rejeb.dataform.language.injection;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The files injected into the hosts of a SQLX file.
 *
 * <p>Every block of a SQLX file hosts another language, and reaching what a block holds always takes
 * the same walk: find the hosts, ask the platform for their injected files, keep each file once.</p>
 */
public final class InjectedFiles {

    private InjectedFiles() {
    }

    /** The files injected into every host of a kind held by {@code hostFile}. */
    public static @NotNull List<PsiFile> inside(@NotNull PsiFile hostFile,
                                                @NotNull Class<? extends PsiElement> hostType) {
        return of(PsiTreeUtil.findChildrenOfType(hostFile, hostType));
    }

    /** The files injected into the given hosts, each listed once and in order. */
    public static @NotNull List<PsiFile> of(@NotNull Collection<? extends PsiElement> hosts) {
        List<PsiFile> files = new ArrayList<>();
        for (PsiElement host : hosts) {
            List<Pair<PsiElement, TextRange>> injected = InjectedLanguageManager
                    .getInstance(host.getProject()).getInjectedPsiFiles(host);
            if (injected == null) continue;
            for (Pair<PsiElement, TextRange> pair : injected) {
                PsiFile file = pair.getFirst().getContainingFile();
                if (file != null && !files.contains(file)) files.add(file);
            }
        }
        return files;
    }
}
