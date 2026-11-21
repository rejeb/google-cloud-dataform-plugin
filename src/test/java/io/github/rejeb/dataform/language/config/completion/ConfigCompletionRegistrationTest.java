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
package io.github.rejeb.dataform.language.config.completion;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.lang.javascript.JavascriptLanguage;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.completion.DataformJsSymbolCompletionContributor;
import io.github.rejeb.dataform.language.completion.DataformRefCompletionContributor;
import io.github.rejeb.dataform.language.unittest.DataformTestDatasetCompletionContributor;

import java.util.List;

public class ConfigCompletionRegistrationTest extends BasePlatformTestCase {

    public void testConfigCompletionRunsAfterRefAndTestDatasetAndBeforeJsSymbols() {
        List<String> names = javaScriptContributors().stream().map(c -> c.getClass().getName()).toList();
        int ref = names.indexOf(DataformRefCompletionContributor.class.getName());
        int dataset = names.indexOf(DataformTestDatasetCompletionContributor.class.getName());
        int config = names.indexOf(SqlxConfigCompletionContributor.class.getName());
        int symbols = names.indexOf(DataformJsSymbolCompletionContributor.class.getName());

        assertTrue(names.toString(), ref >= 0 && ref < dataset && dataset < config && config < symbols);
    }

    public void testTheSplitConfigContributorsAreGone() {
        List<String> simpleNames = javaScriptContributors().stream().map(c -> c.getClass().getSimpleName()).toList();

        for (String gone : List.of("DataformConfigTagCompletionContributor", "DataformConfigValueCompletionContributor",
                "DataformConfigColumnCompletionContributor", "DataformConfigPropertyCompletionContributor")) {
            assertFalse(simpleNames.toString(), simpleNames.contains(gone));
        }
    }

    private static List<CompletionContributor> javaScriptContributors() {
        return CompletionContributor.forLanguage(JavascriptLanguage.INSTANCE);
    }
}
