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
package io.github.rejeb.dataform.language.refactoring.column;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.usageView.UsageInfo;
import io.github.rejeb.dataform.language.lineage.column.ColumnRef;
import io.github.rejeb.dataform.language.schema.sql.ColumnOriginService;
import io.github.rejeb.dataform.language.schema.sql.model.DataformDasColumn;

import java.util.List;

/**
 * A rename started outside an editor — from the column window, the lineage view, Find Usages —
 * renames the column the same way the editor does.
 */
public class ColumnRenameDialogEntryPointTest extends ColumnRenameFixture {

    private DataformDasColumn installAndResolve() {
        installProject(
                new Action("raw", "SELECT 1 AS order_id", List.of(), List.of("order_id")),
                new Action("use", "SELECT order_id FROM `p.d.raw`", List.of("raw"),
                        List.of("order_id")));
        addFile("raw", "config { type: \"table\" }\n\nSELECT 1 AS order_id\n");
        addFile("use", "config { type: \"table\" }\n\nSELECT\n    order_id\nFROM ${ref(\"raw\")}\n");
        DataformDasColumn column = ColumnOriginService.getInstance(getProject())
                .dasColumn(new ColumnRef("p.d.use", "order_id"));
        assertNotNull("the schema must know the column", column);
        return column;
    }

    public void testTheColumnIsClaimedByThisProcessor() {
        DataformDasColumn column = installAndResolve();

        RenamePsiElementProcessor processor = RenamePsiElementProcessor.forElement(column);

        assertTrue("a Dataform column is renamed by this plugin, not by the SQL plugin",
                processor instanceof DataformColumnRenamePsiElementProcessor);
    }

    public void testRenamingThroughTheProcessorWritesEveryPlace() {
        DataformDasColumn column = installAndResolve();
        RenamePsiElementProcessor processor = RenamePsiElementProcessor.forElement(column);

        processor.prepareRenaming(column, "order_ref", new java.util.HashMap<>());
        WriteCommandAction.runWriteCommandAction(getProject(), (Runnable) () ->
                processor.renameElement(column, "order_ref", UsageInfo.EMPTY_ARRAY, null));

        PsiFile use = fileOf("use");
        PsiFile raw = fileOf("raw");
        assertTrue("the column is renamed where it is declared\n" + use.getText(),
                use.getText().contains("order_ref"));
        assertTrue("and where it comes from\n" + raw.getText(),
                raw.getText().contains("AS order_ref"));
    }
}
