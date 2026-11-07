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
package io.github.rejeb.dataform.language.fileEditor;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.UIUtil;
import io.github.rejeb.dataform.language.schema.sql.DryRunErrorRegistry;

import java.awt.Component;
import java.awt.Container;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class SchemaPanelTest extends BasePlatformTestCase {

    private static final String ORDERS = "p.d.orders";

    @Override
    protected void tearDown() throws Exception {
        try {
            DryRunErrorRegistry.getInstance(getProject()).retainOnly(Set.of());
        } catch (Throwable e) {
            addSuppressedException(e);
        } finally {
            super.tearDown();
        }
    }

    public void testTheHeaderSaysWhereTheSchemasComeFrom() {
        SchemaPanel panel = new SchemaPanel(getProject());

        assertEquals("Schemas from BigQuery dry run", panel.header().summaryText());
        assertEquals(UIUtil.getContextHelpForeground(), panel.header().summaryLabel().getForeground());
        assertEquals(JBFont.medium().getSize(), panel.header().summaryLabel().getFont().getSize());
    }

    public void testTheHeaderGivesTheCompilationTime() {
        SchemaPanel panel = new SchemaPanel(getProject());
        long compiledAt = 1_700_000_000_000L;

        panel.showCompilation(compiledAt);

        String clock = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochMilli(compiledAt));
        assertEquals("Schemas from BigQuery dry run · " + clock, panel.header().summaryText());
    }

    public void testActionsWithoutTableAreLeftOut() {
        SchemaPanel panel = new SchemaPanel(getProject());

        panel.setContent(List.of(new GraphTarget("orders", ORDERS, "table", true),
                new GraphTarget("cleanup", "p.d.cleanup", "operation", false)));

        assertEquals(List.of(ORDERS), titles(panel));
    }

    public void testTablesShowTheirFullNameAndAssertionsTheirName() {
        SchemaPanel panel = new SchemaPanel(getProject());

        panel.setContent(List.of(new GraphTarget("orders", ORDERS, "table", true),
                new GraphTarget("orders_assertions_uniqueKey_0", "p.d_assertions.orders_assertions_uniqueKey_0",
                        "assertion", true)));

        assertEquals(List.of(ORDERS, "orders_assertions_uniqueKey_0"), titles(panel));
    }

    public void testAFailedDryRunExplainsTheMissingSchema() {
        DryRunErrorRegistry.getInstance(getProject()).report(ORDERS, "Unrecognized name: x");
        SchemaPanel panel = new SchemaPanel(getProject());

        panel.setContent(List.of(new GraphTarget("orders", ORDERS, "table", true)));

        TableSchemaSection section = sections(panel).getFirst();
        assertTrue(section.view().getEmptyText().getSecondaryComponent().getCharSequence(false).toString()
                .startsWith("The query has errors."));
    }

    private static List<String> titles(SchemaPanel panel) {
        return sections(panel).stream().map(TableSchemaSection::title).toList();
    }

    private static List<TableSchemaSection> sections(Component root) {
        List<TableSchemaSection> found = new ArrayList<>();
        if (root instanceof TableSchemaSection section) found.add(section);
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) found.addAll(sections(child));
        }
        return found;
    }
}
