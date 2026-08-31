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

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

/**
 * The config block is an injection host, so the platform writes changes made inside its injected
 * JavaScript back through the manipulator.
 *
 * <p>Indentation is not asserted: the platform re-indents the lines of a replaced node that follow
 * the first one. What must hold is that the change lands where it was asked for and that nothing
 * else of the file is lost.</p>
 */
public class SqlxConfigBlockManipulatorTest extends BasePlatformTestCase {

    public void testAChangeIsSplicedIntoTheBlockAndNothingElse() {
        PsiFile file = myFixture.configureByText("action.sqlx",
                "config {\n  type: \"table\",\n  tags: [\"a\"]\n}\n\nSELECT 1 AS x\n");
        SqlxConfigBlock block = PsiTreeUtil.findChildOfType(file, SqlxConfigBlock.class);
        assertNotNull(block);
        int start = block.getText().indexOf("\"a\"");

        WriteCommandAction.runWriteCommandAction(getProject(), (Runnable) () ->
                new SqlxConfigBlockManipulator().handleContentChange(block,
                        TextRange.from(start, "\"a\"".length()), "\"b\""));

        assertTrue("the change is spliced into the block\n" + file.getText(),
                file.getText().contains("tags: [\"b\"]"));
        assertTrue("what the block already held is kept\n" + file.getText(),
                file.getText().contains("type: \"table\""));
        assertTrue("the rest of the file is untouched\n" + file.getText(),
                file.getText().endsWith("SELECT 1 AS x\n"));
        assertFalse("the block is not replaced by a copy of the file",
                file.getText().contains("SELECT 1 AS x\n}"));
    }
}
