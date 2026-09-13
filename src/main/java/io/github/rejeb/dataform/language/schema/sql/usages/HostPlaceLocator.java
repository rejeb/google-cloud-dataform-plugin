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
package io.github.rejeb.dataform.language.schema.sql.usages;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Finds the place in a host file an occurrence of a column sits in.
 *
 * <p>One search hits the same file many times, and every hit would otherwise walk from the injected
 * fragment up to the host file, then to its document and its virtual file. Those three are a
 * property of the file, not of the hit, so they are resolved once and kept for the rest of the
 * run.</p>
 *
 * <p>A search runs on several threads, so the cache is concurrent. It holds only what one run
 * resolved: a locator is made for a run and dropped with it, which is what keeps a cached document
 * from outliving the file it belongs to.</p>
 */
final class HostPlaceLocator {

    private final InjectedLanguageManager injections;
    private final PsiDocumentManager documents;
    private final Map<PsiFile, HostFile> hosts = new ConcurrentHashMap<>();

    HostPlaceLocator(@NotNull Project project) {
        this.injections = InjectedLanguageManager.getInstance(project);
        this.documents = PsiDocumentManager.getInstance(project);
    }

    /**
     * The place an element sits in its host file, or {@code null} when it has no place a row could
     * open: no containing file, no document, or an offset the document no longer holds.
     */
    @Nullable HostPlace locate(@NotNull PsiElement element) {
        PsiFile containing = element.getContainingFile();
        if (containing == null) return null;
        HostFile host = hostOf(containing);
        if (host == null) return null;
        int offset = injections.injectedToHost(element, element.getTextOffset());
        if (offset < 0 || offset >= host.document().getTextLength()) return null;
        return new HostPlace(host.host(), host.file(), host.document(), offset,
                host.document().getLineNumber(offset));
    }

    private @Nullable HostFile hostOf(@NotNull PsiFile containing) {
        HostFile known = hosts.get(containing);
        if (known != null) return known;
        PsiFile host = injections.getTopLevelFile(containing);
        Document document = documents.getDocument(host);
        VirtualFile file = host.getVirtualFile();
        if (document == null || file == null) return null;
        HostFile found = new HostFile(host, file, document);
        HostFile raced = hosts.putIfAbsent(containing, found);
        return raced != null ? raced : found;
    }

    private record HostFile(@NotNull PsiFile host, @NotNull VirtualFile file,
                            @NotNull Document document) {
    }
}
