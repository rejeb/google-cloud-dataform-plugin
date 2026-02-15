/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
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

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import io.github.rejeb.dataform.language.SqlxLanguage;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

public class SqlxSqlBlockManipulator extends AbstractElementManipulator<SqlxSqlBlock> {

    @Override
    public SqlxSqlBlock handleContentChange(@NotNull SqlxSqlBlock element,
                                            @NotNull TextRange range,
                                            @NotNull String newContent)
            throws IncorrectOperationException {
        String oldText = element.getText();
        String newText = oldText.substring(0, range.getStartOffset())
                + newContent
                + oldText.substring(range.getEndOffset());
        PsiFile fileFromText = PsiFileFactory.getInstance(element.getProject())
                .createFileFromText("dummy.sqlx",
                        SqlxLanguage.INSTANCE,
                        newText);

        SqlxSqlBlock newElement = PsiTreeUtil.findChildOfType(fileFromText, SqlxSqlBlock.class);
        return (SqlxSqlBlock) element.replace(newElement);
    }

    @Override
    public @NonNull TextRange getRangeInElement(@NotNull SqlxSqlBlock element) {

        return TextRange.from(0, element.getTextLength());
    }
}