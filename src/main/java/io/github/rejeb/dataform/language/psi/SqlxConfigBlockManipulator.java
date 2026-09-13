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

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import io.github.rejeb.dataform.language.SqlxLanguage;
import org.jetbrains.annotations.NotNull;

/**
 * Writes a change into the config block of a SQLX file.
 *
 * <p>The block is an injection host, so the platform asks it to splice a new piece of text into
 * itself whenever something inside the injected JavaScript is rewritten. Only the block is parsed
 * again — the platform calls this once per rewritten reference, and re-parsing the whole SQLX file
 * each time would parse every other block of the file for nothing.</p>
 *
 * <p>The element holds the content of the block, not the {@code config { }} around it, so the new
 * text is wrapped back into a block before it is parsed. Parsed on its own it would read as the SQL
 * of the file and no config block would come back.</p>
 */
public class SqlxConfigBlockManipulator extends AbstractElementManipulator<SqlxConfigBlock> {

    private static final String BLOCK_PREFIX = "config {";
    private static final String BLOCK_SUFFIX = "}";

    @Override
    public SqlxConfigBlock handleContentChange(@NotNull SqlxConfigBlock element,
                                               @NotNull TextRange range,
                                               @NotNull String newContent)
            throws IncorrectOperationException {
        String oldText = element.getText();
        String newText = oldText.substring(0, range.getStartOffset())
                + newContent
                + oldText.substring(range.getEndOffset());
        PsiFile fileFromText = PsiFileFactory.getInstance(element.getProject())
                .createFileFromText("dummy.sqlx", SqlxLanguage.INSTANCE,
                        BLOCK_PREFIX + newText + BLOCK_SUFFIX);

        SqlxConfigBlock newElement = PsiTreeUtil.findChildOfType(fileFromText, SqlxConfigBlock.class);
        if (newElement == null) {
            throw new IncorrectOperationException("The config block does not parse after the change");
        }
        return (SqlxConfigBlock) element.replace(newElement);
    }

    @Override
    public @NotNull TextRange getRangeInElement(@NotNull SqlxConfigBlock element) {
        return TextRange.from(0, element.getTextLength());
    }
}
