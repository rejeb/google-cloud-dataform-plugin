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

import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.Container;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * The name in the header of a section is selectable text, which takes the clicks made on it. The
 * rest of the header must still take a click as a request to collapse the section.
 */
public class TableSectionHeaderTest extends BasePlatformTestCase {

    public void testTheHeaderOfASchemaSectionCollapsesItBesideTheName() {
        TableSchemaSection section = new TableSchemaSection("orders", "table",
                List.of(new ColumnInfo("id", "STRING", "NULLABLE", null)), null, () -> {
        });

        assertCollapsesBesideTheName(section);
    }

    public void testTheHeaderOfAQuerySectionCollapsesItBesideTheName() {
        TableQuerySection section = new TableQuerySection(
                new FormattedCompiledQuery("orders", null, null, "SELECT 1", null, null),
                PlainTextFileType.INSTANCE, getProject());
        try {
            assertCollapsesBesideTheName(section);
        } finally {
            section.dispose();
        }
    }

    public void testAClickOnTheCostLabelCollapsesTheSection() {
        TableQuerySection section = new TableQuerySection(
                new FormattedCompiledQuery("orders", null, null, "SELECT 1", null, null),
                PlainTextFileType.INSTANCE, getProject());
        try {
            section.setSize(800, 600);
            layOut(section);
            CostLabel label = section.costLabel();
            label.setToolTipText("Dry-run estimate of the query shown.");

            label.dispatchEvent(new MouseEvent(label, MouseEvent.MOUSE_CLICKED, 0, 0, 1, 1, 1, false));

            assertFalse(section.getComponent(1).isVisible());
        } finally {
            section.dispose();
        }
    }

    private static void assertCollapsesBesideTheName(JPanel section) {
        section.setSize(800, 600);
        layOut(section);
        JComponent header = (JComponent) section.getComponent(0);
        int x = header.getWidth() / 2;
        int y = header.getHeight() / 2;

        Component target = SwingUtilities.getDeepestComponentAt(header, x, y);
        assertFalse(target instanceof JTextComponent);
        Point point = SwingUtilities.convertPoint(header, x, y, target);
        target.dispatchEvent(new MouseEvent(target, MouseEvent.MOUSE_CLICKED, 0, 0, point.x, point.y, 1, false));
        assertFalse(section.getComponent(1).isVisible());
    }

    private static void layOut(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) {
            if (child instanceof Container nested) layOut(nested);
        }
    }
}
