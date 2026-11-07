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

import com.intellij.ui.treeStructure.treetable.ListTreeTableModelOnColumns;
import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;
import org.junit.jupiter.api.Test;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class SchemaTreeModelTest {

    @Test
    void theColumnsAreNameTypeModeAndDescription() {
        ListTreeTableModelOnColumns model = SchemaTreeModel.of(List.of());

        assertEquals(List.of("Name", "Type", "Mode", "Description"),
                List.of(model.getColumnName(0), model.getColumnName(1), model.getColumnName(2), model.getColumnName(3)));
    }

    @Test
    void aTopLevelColumnIsItsName() {
        ListTreeTableModelOnColumns model = SchemaTreeModel.of(List.of(new ColumnInfo("id", "INT64", "REQUIRED", null)));

        assertEquals("id", SchemaTreeModel.pathOf(model.getChild(model.getRoot(), 0)));
    }

    @Test
    void aFieldIsItsPathFromTheTopColumn() {
        ColumnInfo lat = new ColumnInfo("lat", "FLOAT64", "NULLABLE", null);
        ColumnInfo geo = new ColumnInfo("geo", "RECORD", "NULLABLE", null, List.of(lat));
        ColumnInfo address = new ColumnInfo("address", "RECORD", "REPEATED", null, List.of(geo));
        ListTreeTableModelOnColumns model = SchemaTreeModel.of(List.of(address));

        Object nodeAddress = model.getChild(model.getRoot(), 0);
        Object nodeGeo = model.getChild(nodeAddress, 0);

        assertEquals("address.geo", SchemaTreeModel.pathOf(nodeGeo));
        assertEquals("address.geo.lat", SchemaTreeModel.pathOf(model.getChild(nodeGeo, 0)));
    }

    @Test
    void theRootHasNoPath() {
        assertEquals("", SchemaTreeModel.pathOf(SchemaTreeModel.of(List.of()).getRoot()));
    }

    @Test
    void testNestedRecordsBecomeChildren() {
        ColumnInfo c = new ColumnInfo("c", "STRING", "NULLABLE", "leaf");
        ColumnInfo b = new ColumnInfo("b", "RECORD", "REPEATED", null, List.of(c));
        ColumnInfo a = new ColumnInfo("a", "RECORD", "NULLABLE", null, List.of(b));
        ColumnInfo id = new ColumnInfo("id", "INT64", "REQUIRED", null);

        ListTreeTableModelOnColumns model = SchemaTreeModel.of(List.of(a, id));

        Object root = model.getRoot();
        assertEquals(2, model.getChildCount(root));
        Object nodeA = model.getChild(root, 0);
        Object nodeB = model.getChild(nodeA, 0);
        Object nodeC = model.getChild(nodeB, 0);
        assertSame(c, ((DefaultMutableTreeNode) nodeC).getUserObject());
        assertEquals("STRING", model.getValueAt(nodeC, 1));
        assertEquals("NULLABLE", model.getValueAt(nodeC, 2));
        assertEquals("leaf", model.getValueAt(nodeC, 3));
        assertEquals("REPEATED", model.getValueAt(nodeB, 2));
        assertEquals(0, model.getChildCount(model.getChild(root, 1)));
    }

    @Test
    void aMissingDescriptionIsEmpty() {
        ListTreeTableModelOnColumns model = SchemaTreeModel.of(List.of(new ColumnInfo("id", "INT64", "REQUIRED", null)));

        assertEquals("", model.getValueAt(model.getChild(model.getRoot(), 0), 3));
    }

    @Test
    void theColumnNamesAreTheTopLevelNames() {
        ColumnInfo a = new ColumnInfo("a", "RECORD", "NULLABLE", null, List.of(new ColumnInfo("x", "STRING", "NULLABLE", null)));

        assertEquals("a\nid", SchemaTreeModel.columnNames(List.of(a, new ColumnInfo("id", "INT64", "REQUIRED", null))));
    }

    @Test
    void theValueColumnsAreSelectableTextButTheNameIsTheTree() {
        ListTreeTableModelOnColumns model = SchemaTreeModel.of(List.of(new ColumnInfo("id", "INT64", "REQUIRED", null)));
        Object node = model.getChild(model.getRoot(), 0);

        assertEquals(List.of(false, true, true, true), List.of(model.isCellEditable(node, 0),
                model.isCellEditable(node, 1), model.isCellEditable(node, 2), model.isCellEditable(node, 3)));
    }
}
