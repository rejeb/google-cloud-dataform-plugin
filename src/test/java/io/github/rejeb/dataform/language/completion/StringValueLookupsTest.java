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
package io.github.rejeb.dataform.language.completion;

import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class StringValueLookupsTest extends BasePlatformTestCase {

    public void testWithQuotesInsertsTheQuotedValueAndShowsItBare() {
        LookupElementBuilder element = StringValueLookups.of("daily", true);

        assertEquals("\"daily\"", element.getLookupString());
        assertTrue(element.getAllLookupStrings().contains("daily"));
        assertEquals("daily", render(element).getItemText());
    }

    public void testWithoutQuotesInsertsTheBareValue() {
        LookupElementBuilder element = StringValueLookups.of("daily", false);

        assertEquals("daily", element.getLookupString());
        assertEquals("daily", render(element).getItemText());
    }

    private static LookupElementPresentation render(LookupElementBuilder element) {
        LookupElementPresentation presentation = new LookupElementPresentation();
        element.renderElement(presentation);
        return presentation;
    }
}
