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
package io.github.rejeb.dataform.language.lineage.view;

import io.github.rejeb.dataform.language.columns.model.ColumnRef;
import io.github.rejeb.dataform.language.lineage.layout.NodePosition;
import io.github.rejeb.dataform.language.lineage.model.CardColumns;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NodeCardMetricsTest {

    private static CardColumns columns(int rows, int hidden) {
        List<ColumnRef> list = new ArrayList<>();
        for (int i = 0; i < rows; i++) list.add(new ColumnRef("p.d.t", "c" + i));
        return new CardColumns(list, hidden);
    }

    @Test
    void aCardWithoutRowsAddsNoHeight() {
        assertEquals(0, NodeCardMetrics.rowsHeight(columns(0, 0), false));
    }

    @Test
    void eachRowIsTwentyFourPixelsHigh() {
        assertEquals(3 * 24, NodeCardMetrics.rowsHeight(columns(3, 0), false));
    }

    @Test
    void theMoreColumnsFooterAddsTwentyTwoPixels() {
        assertEquals(12 * 24 + 22, NodeCardMetrics.rowsHeight(columns(12, 3), false));
    }

    @Test
    void aNoteAddsTwentyTwoPixels() {
        assertEquals(2 * 24 + 22, NodeCardMetrics.rowsHeight(columns(2, 0), true));
        assertEquals(22, NodeCardMetrics.rowsHeight(columns(0, 0), true));
    }

    @Test
    void rowsAreStackedUnderTheHeader() {
        NodePosition pos = new NodePosition("n", 100, 40, 0, 170);
        assertEquals(new Rectangle(100, 40 + 52 + 2 * 24, 170, 24), NodeCardMetrics.rowBounds(pos, 52, 2));
    }

    @Test
    void theFooterSitsUnderTheLastRowAndTheNoteUnderTheFooter() {
        NodePosition pos = new NodePosition("n", 100, 40, 0, 170);
        assertEquals(new Rectangle(100, 40 + 52 + 12 * 24, 170, 22), NodeCardMetrics.footerBounds(pos, 52, columns(12, 3)));
        assertEquals(new Rectangle(100, 40 + 52 + 12 * 24 + 22, 170, 22), NodeCardMetrics.noteBounds(pos, 52, columns(12, 3)));
        assertEquals(new Rectangle(100, 40 + 52 + 2 * 24, 170, 22), NodeCardMetrics.noteBounds(pos, 52, columns(2, 0)));
    }
}
