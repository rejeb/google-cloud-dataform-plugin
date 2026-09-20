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
package io.github.rejeb.dataform.language.folding;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.ui.UIUtil;
import io.github.rejeb.dataform.language.evaluation.DataformEvaluationResult;
import io.github.rejeb.dataform.language.evaluation.DataformExpressionEvaluationService;
import io.github.rejeb.dataform.language.evaluation.DataformExpressionEvaluationServiceImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

/**
 * The values a pass computes after the file came back into focus reach the editor: a hole edited
 * meanwhile folds to its new value, through the same refresh a pass runs in the IDE.
 */
public class DataformFocusPassFoldingTest extends DataformFoldingTestCase {

    private DataformExpressionEvaluationServiceImpl service() {
        return (DataformExpressionEvaluationServiceImpl)
                DataformExpressionEvaluationService.getInstance(getProject());
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            service().resetEvaluator();
        } finally {
            super.tearDown();
        }
    }

    /** Runs a pass the way the service does in the IDE: off the event thread, then lets it apply. */
    private void passInBackground(PsiFile file) throws Exception {
        Future<?> pass = ApplicationManager.getApplication().executeOnPooledThread(
                () -> service().runPassNow(file.getVirtualFile()));
        while (!pass.isDone()) {
            UIUtil.dispatchAllInvocationEvents();
            Thread.sleep(10);
        }
        pass.get();
        UIUtil.dispatchAllInvocationEvents();
        myFixture.doHighlighting();
        UIUtil.dispatchAllInvocationEvents();
    }

    private List<String> collapsedPlaceholders() {
        List<String> out = new ArrayList<>();
        for (FoldRegion region : dataformRegions()) {
            if (!region.isExpanded()) out.add(region.getPlaceholderText());
        }
        return out;
    }

    public void testAHoleEditedWhileOpenFoldsToItsNewValueAfterTheNextPass() throws Exception {
        PsiFile file = configureDefinition("mart.sqlx",
                "config { type: \"table\" }\n\nSELECT * FROM ${ref(\"users\")}\n");
        service().setEvaluator((psi, sources) -> {
            List<DataformEvaluationResult> results = new ArrayList<>();
            for (String source : sources) {
                results.add(DataformEvaluationResult.resolved(source, "value of " + source));
            }
            return results;
        });
        passInBackground(file);
        assertEquals(List.of("value of ref(\"users\")"), collapsedPlaceholders());

        FoldRegion region = dataformRegions().getFirst();
        myFixture.getEditor().getFoldingModel().runBatchFoldingOperation(() -> region.setExpanded(true));
        int hole = myFixture.getEditor().getDocument().getText().indexOf("users");
        WriteCommandAction.runWriteCommandAction(getProject(),
                () -> myFixture.getEditor().getDocument().insertString(hole, "new_"));
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
        myFixture.doHighlighting();
        assertEquals("nothing folds while the file is being edited", List.of(), collapsedPlaceholders());

        passInBackground(file);
        assertEquals("the edited hole folds to its new value once a pass ran",
                List.of("value of ref(\"new_users\")"), collapsedPlaceholders());
    }
    /**
     * Coming back to a file shows its values again, whether or not anything changed: the code
     * behind a value was expanded to be edited, and the person is done with it when they leave.
     */
    public void testAnExpandedValueFoldsAgainAfterAPassEvenWhenNothingChanged() throws Exception {
        PsiFile file = configureDefinition("mart.sqlx",
                "config { type: \"table\" }\n\nSELECT * FROM ${ref(\"users\")}\n");
        service().setEvaluator((psi, sources) -> {
            List<DataformEvaluationResult> results = new ArrayList<>();
            for (String source : sources) {
                results.add(DataformEvaluationResult.resolved(source, "value of " + source));
            }
            return results;
        });
        passInBackground(file);
        assertEquals(List.of("value of ref(\"users\")"), collapsedPlaceholders());

        FoldRegion region = dataformRegions().getFirst();
        myFixture.getEditor().getFoldingModel().runBatchFoldingOperation(() -> region.setExpanded(true));
        assertEquals(List.of(), collapsedPlaceholders());

        passInBackground(file);
        assertEquals("the value is shown again on the next pass",
                List.of("value of ref(\"users\")"), collapsedPlaceholders());
    }
}
