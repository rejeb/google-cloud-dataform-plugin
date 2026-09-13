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

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.sql.psi.SqlCompositeElementTypes;
import com.intellij.util.Processor;
import io.github.rejeb.dataform.language.schema.sql.SqlPsiParts;
import io.github.rejeb.dataform.language.schema.sql.StructColumnPathResolver;
import io.github.rejeb.dataform.language.schema.sql.model.StructColumnPath;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * The reads of a field inside a struct column.
 *
 * <p>No reference points at a field, so the files are narrowed by the one thing the index does know:
 * the word the field is named by. Every occurrence of that word is then read back as a path and kept
 * only when it names this very field, which is what tells {@code customer.origin.code} from the
 * {@code code} of another column entirely. The resolver doing the reading is the one navigation
 * uses, so the window and a click agree by construction.</p>
 *
 * <p>A field may be named something as common as {@code name} or {@code id}, and a word that common
 * carries far more occurrences than a window will ever show. The occurrences examined are therefore
 * bounded as well as the rows collected: the work is paid for while a reader waits, and a reader
 * waits for the first rows, not the last.</p>
 */
final class StructFieldColumnUsageSearch implements ColumnUsageSearch {

    /**
     * Ceiling on the occurrences read back. A multiple of the rows shown, so a field whose name is
     * written in many places still fills the window before the search gives up.
     */
    private static final int CANDIDATES_PER_ROW = 20;

    private final Project project;
    private final StructColumnPath path;

    StructFieldColumnUsageSearch(@NotNull Project project, @NotNull StructColumnPath path) {
        this.project = project;
        this.path = path;
    }

    @Override
    public void forEachRead(@NotNull Processor<PsiElement> reads, int maxReads) {
        StructColumnPathResolver resolver = StructColumnPathResolver.getInstance(project);
        long maxExamined = (long) CANDIDATES_PER_ROW * maxReads;
        AtomicInteger examined = new AtomicInteger();
        PsiSearchHelper.getInstance(project).processElementsWithWord(
                (element, offsetInElement) -> {
                    ProgressManager.checkCanceled();
                    if (examined.incrementAndGet() > maxExamined) {
                        return false;
                    }
                    StructColumnPath found = resolver.pathAt(element);
                    if (found == null || !found.sameAs(path)) return true;
                    return reads.process(identifierOf(element));
                },
                GlobalSearchScope.projectScope(project),
                path.leafName(),
                UsageSearchContext.IN_CODE,
                true);
    }

    /**
     * The name the row is drawn around. The index reports the token holding the word, and a row
     * names the identifier it belongs to: a field may be named by a word the dialect also keeps as a
     * keyword, and the token then carries a type of its own while the identifier never does.
     */
    private static @NotNull PsiElement identifierOf(@NotNull PsiElement element) {
        PsiElement identifier = element.getParent();
        return SqlPsiParts.isType(identifier, SqlCompositeElementTypes.SQL_IDENTIFIER)
                ? identifier
                : element;
    }
}
