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
package io.github.rejeb.dataform.language.psi;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SqlxConfigBlocks {

    private SqlxConfigBlocks() {
    }

    /**
     * Returns the config block whose first leaf is the given element, climbing first children only so
     * that other leaves stop after a step or two instead of walking up to the file.
     */
    @Nullable
    public static SqlxConfigBlock startingWith(@NotNull PsiElement leaf) {
        PsiElement node = leaf;
        while (!(node instanceof SqlxConfigBlock block)) {
            PsiElement parent = node.getParent();
            if (parent == null || parent instanceof PsiFile || parent.getFirstChild() != node) {
                return null;
            }
            node = parent;
        }
        return block;
    }
}
