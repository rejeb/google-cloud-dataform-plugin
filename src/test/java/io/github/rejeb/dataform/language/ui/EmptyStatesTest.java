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
package io.github.rejeb.dataform.language.ui;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.StatusText;


public class EmptyStatesTest extends BasePlatformTestCase {

    public void testPrimaryTextReplacesTheDefault() {
        StatusText text = new JBList<String>().getEmptyText();

        EmptyStates.show(text, "No repositories");

        assertEquals("No repositories", text.getText());
    }

    public void testTheLinkIsShownAsALinkBelowThePrimaryText() {
        StatusText text = new JBList<String>().getEmptyText();

        EmptyStates.show(text, "No schema yet", "Refresh schema", () -> {
        });

        assertEquals("No schema yet", text.getText());
        assertEquals("Refresh schema", text.getSecondaryComponent().getCharSequence(false).toString());
        SimpleColoredComponent.ColoredIterator fragments = text.getSecondaryComponent().iterator();
        fragments.next();
        assertEquals(SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES, fragments.getTextAttributes());
    }

    public void testAnExplanationCanPrecedeTheLinkOnTheSameLine() {
        StatusText text = new JBList<String>().getEmptyText();

        EmptyStates.show(text, "No schema yet", "The table does not exist in BigQuery yet.", "Refresh schema", () -> {
        });

        assertEquals("No schema yet", text.getText());
        assertEquals("The table does not exist in BigQuery yet. Refresh schema",
                text.getSecondaryComponent().getCharSequence(false).toString());
        SimpleColoredComponent.ColoredIterator fragments = text.getSecondaryComponent().iterator();
        fragments.next();
        assertEquals(SimpleTextAttributes.GRAYED_ATTRIBUTES, fragments.getTextAttributes());
        fragments.next();
        assertEquals(SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES, fragments.getTextAttributes());
    }
}
