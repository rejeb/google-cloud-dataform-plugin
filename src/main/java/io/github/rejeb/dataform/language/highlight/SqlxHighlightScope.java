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
package io.github.rejeb.dataform.language.highlight;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import io.github.rejeb.dataform.language.psi.SqlxFile;
import org.jetbrains.annotations.Nullable;

/**
 * Tells whether a problem was reported inside a SQLX file, injected fragments included.
 */
public final class SqlxHighlightScope {

    private SqlxHighlightScope() {
    }

    /**
     * Returns true when the element belongs to a SQLX file, either directly or through an injected
     * fragment of one.
     */
    public static boolean isInSqlxFile(@Nullable PsiElement element) {
        return element != null && isInSqlxFile(element.getContainingFile());
    }

    /**
     * Returns true when the file is a SQLX file or a fragment injected into one.
     */
    public static boolean isInSqlxFile(@Nullable PsiFile file) {
        if (file == null) {
            return false;
        }
        if (file instanceof SqlxFile) {
            return true;
        }
        PsiFile topLevel = InjectedLanguageManager.getInstance(file.getProject())
                .getTopLevelFile(file);
        return topLevel instanceof SqlxFile;
    }
}
