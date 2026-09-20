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

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;

/**
 * Rebuilds what the platform computed for a file before its template values arrived.
 *
 * <p>The platform keeps an injection, and the folding, for as long as the host file and the PSI
 * modification count stand still, and a value computed after the fact moves neither. Dropping the
 * PSI caches moves the count, which is what makes the next pass inject the SQL and fold the holes
 * again, this time with the values; the injection's own file caches are dropped alongside so no
 * window over the old text is handed out meanwhile.</p>
 *
 * <p>Runs on the event thread, from {@code DataformFoldingRefresher}, which computes the folding
 * only once this is done.</p>
 */
public final class SqlxInjectionRefresher {

    private static final String RESTART_REASON = "Dataform template values updated";

    private SqlxInjectionRefresher() {
    }

    /**
     * Drops the injections and PSI caches of a file and asks for it to be highlighted again. Runs
     * on the event thread, in a write-safe context, where the caches may be dropped and the daemon
     * restarted.
     */
    public static void refresh(@NotNull Project project, @NotNull VirtualFile file) {
        if (project.isDisposed() || !file.isValid()) return;
        PsiManager psiManager = PsiManager.getInstance(project);
        PsiFile psiFile = psiManager.findFile(file);
        if (psiFile == null) return;
        InjectedLanguageManager.getInstance(project).dropFileCaches(psiFile);
        psiManager.dropPsiCaches();
        DaemonCodeAnalyzer.getInstance(project).restart(psiFile, RESTART_REASON);
    }
}
