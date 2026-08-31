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
package io.github.rejeb.dataform.language.refactoring.column.usage;

import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import io.github.rejeb.dataform.language.injection.InjectedFiles;
import io.github.rejeb.dataform.language.psi.SharedTokenTypes;
import io.github.rejeb.dataform.language.psi.SqlxSqlBlock;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * The injected SQL of a SQLX file.
 *
 * <p>A SQLX file holds several SQL blocks: the query building the table, and the operations run
 * before and after it. The query alone declares the columns of the table; all of them read them.</p>
 */
public final class InjectedSqlFiles {

    private InjectedSqlFiles() {
    }

    /** The injected files of the block holding the query the action is built from. */
    public static @NotNull List<PsiFile> mainQuery(@NotNull PsiFile hostFile) {
        return injectedIn(hostFile, true);
    }

    /** The injected files of every SQL block of the file, operations included. */
    public static @NotNull List<PsiFile> all(@NotNull PsiFile hostFile) {
        return injectedIn(hostFile, false);
    }

    private static @NotNull List<PsiFile> injectedIn(@NotNull PsiFile hostFile, boolean mainOnly) {
        List<SqlxSqlBlock> blocks = new ArrayList<>();
        for (SqlxSqlBlock block : PsiTreeUtil.findChildrenOfType(hostFile, SqlxSqlBlock.class)) {
            if (mainOnly && block.getNode().getElementType() != SharedTokenTypes.SQL_CONTENT) {
                continue;
            }
            blocks.add(block);
        }
        return InjectedFiles.of(blocks);
    }
}
