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

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class SqlxInputBlock extends SqlxPsiElement {

    private static final TokenSet NAMES = TokenSet.create(SharedTokenTypes.INPUT_NAME);

    public SqlxInputBlock(@NotNull ASTNode node) {
        super(node);
    }

    /**
     * Returns the parts of the input label without their quotes, in declaration order.
     */
    @NotNull
    public List<String> labelParts() {
        List<String> parts = new ArrayList<>();
        for (ASTNode name : getNode().getChildren(NAMES)) {
            parts.add(StringUtil.unquoteString(name.getText()));
        }
        return parts;
    }

    /**
     * Returns the SQL block holding the mocked rows, or null when the block has no body.
     */
    @Nullable
    public SqlxSqlBlock content() {
        return PsiTreeUtil.getChildOfType(this, SqlxSqlBlock.class);
    }
}
