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

import com.intellij.openapi.editor.FoldRegion;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.evaluation.DataformExpressionEvaluationService;
import io.github.rejeb.dataform.language.evaluation.DataformExpressionEvaluationServiceImpl;

import java.util.Arrays;
import java.util.List;

/**
 * Shared setup for folding tests: seeds the evaluation cache so no Node process is needed.
 */
public abstract class DataformFoldingTestCase extends BasePlatformTestCase {

    protected PsiFile configureDefinition(String name, String text) {
        PsiFile file = myFixture.addFileToProject("definitions/" + name, text);
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        return file;
    }

    protected void seed(PsiFile file, String source, String value) {
        DataformExpressionEvaluationService service =
                DataformExpressionEvaluationService.getInstance(getProject());
        ((DataformExpressionEvaluationServiceImpl) service)
                .putCachedValue(file.getVirtualFile(), source, value);
    }

    protected List<FoldRegion> dataformRegions() {
        myFixture.doHighlighting();
        return Arrays.stream(myFixture.getEditor().getFoldingModel().getAllFoldRegions())
                .filter(region -> DataformFoldingPlaceholder.isDataformRegion(region.getGroup()))
                .toList();
    }

    /**
     * Regions of injected roots carry no folding group, so they are looked up by placeholder.
     */
    protected List<FoldRegion> regionsWithPlaceholder(String placeholder) {
        myFixture.doHighlighting();
        return Arrays.stream(myFixture.getEditor().getFoldingModel().getAllFoldRegions())
                .filter(region -> placeholder.equals(region.getPlaceholderText()))
                .toList();
    }
}
