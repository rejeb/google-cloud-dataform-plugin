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
package io.github.rejeb.dataform.language.lineage.toolwindow;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.StatusText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

public class LineageToolWindowEmptyStatesTest extends BasePlatformTestCase {

    private final List<ActionListener> links = new ArrayList<>();
    private final List<String> shown = new ArrayList<>();

    private StatusText text() {
        return new StatusText() {
            @Override
            protected boolean isStatusVisible() {
                return true;
            }

            @Override
            public @NotNull StatusText appendSecondaryText(@NotNull String text, @NotNull SimpleTextAttributes attrs,
                                                           @Nullable ActionListener listener) {
                if (listener != null) links.add(listener);
                return super.appendSecondaryText(text, attrs, listener);
            }
        };
    }

    public void testWithoutAnEditorOrWithTheDataformSourceTheLinkShowsTheKnowledgeCatalogLineage() {
        for (LineageToolWindowState state : List.of(LineageToolWindowState.NO_EDITOR, LineageToolWindowState.DATAFORM_SOURCE)) {
            links.clear();
            shown.clear();
            StatusText text = text();

            LineageToolWindowEmptyStates.show(text, state, () -> shown.add("catalog"));

            assertEquals("Runs, trace and differences come from Knowledge Catalog", EmptyStatusTexts.primary(text));
            assertEquals("Show the Knowledge Catalog lineage", EmptyStatusTexts.link(text));
            assertEquals(1, links.size());
            links.getFirst().actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "link"));
            assertEquals(List.of("catalog"), shown);
        }
    }

    public void testWithoutASelectionItAsksForATable() {
        StatusText text = text();

        LineageToolWindowEmptyStates.show(text, LineageToolWindowState.NO_SELECTION, () -> shown.add("catalog"));

        assertEquals("Select a table in the lineage view", EmptyStatusTexts.primary(text));
        assertNull(EmptyStatusTexts.link(text));
        assertTrue(links.isEmpty());
    }

    public void testAStateTheTabWritesItselfLeavesTheTextEmpty() {
        StatusText text = text();
        text.setText("old");

        LineageToolWindowEmptyStates.show(text, LineageToolWindowState.READY, () -> shown.add("catalog"));

        assertEquals("", EmptyStatusTexts.primary(text));
    }
}
