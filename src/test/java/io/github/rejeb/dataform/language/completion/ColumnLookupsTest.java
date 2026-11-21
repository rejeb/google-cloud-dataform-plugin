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
import com.intellij.icons.AllIcons;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;

public class ColumnLookupsTest extends BasePlatformTestCase {

    public void testAScalarColumnShowsItsTypeAndDescription() {
        LookupElementPresentation presentation = render(ColumnLookups.describe(LookupElementBuilder.create("amount"),
                new ColumnInfo("amount", "NUMERIC", "NULLABLE", "Total"), "NUMERIC"));

        assertEquals("NUMERIC", presentation.getTypeText());
        assertEquals(" Total", presentation.getTailText());
        assertTrue(presentation.getTailFragments().get(0).isGrayed());
        assertSame(AllIcons.Nodes.Field, presentation.getIcon());
    }

    public void testARepeatedColumnShowsBrackets() {
        LookupElementPresentation presentation = render(ColumnLookups.describe(LookupElementBuilder.create("items"),
                new ColumnInfo("items", "RECORD", "REPEATED", null), "STRUCT"));

        assertEquals("STRUCT[]", presentation.getTypeText());
        assertNull(presentation.getTailText());
    }

    public void testABlankDescriptionAddsNoTail() {
        LookupElementPresentation presentation = render(ColumnLookups.describe(LookupElementBuilder.create("id"),
                new ColumnInfo("id", "INT64", "NULLABLE", "  "), "INT64"));

        assertNull(presentation.getTailText());
    }

    private static LookupElementPresentation render(LookupElementBuilder element) {
        LookupElementPresentation presentation = new LookupElementPresentation();
        element.renderElement(presentation);
        return presentation;
    }
}
