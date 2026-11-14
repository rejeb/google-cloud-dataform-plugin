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

import java.util.List;
import io.github.rejeb.dataform.language.lineage.model.CardColumns;
import io.github.rejeb.dataform.language.lineage.layout.NodePosition;
import io.github.rejeb.dataform.language.columns.model.ColumnRef;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CanvasHitTestColumnTest {

    private static Map<String, Rectangle> bounds() {
        Map<String, Rectangle> b = new LinkedHashMap<>();
        b.put("p.d.src#amount", new Rectangle(0, 50, 180, 14));
        b.put("p.d.mid#total", new Rectangle(300, 150, 180, 14));
        return b;
    }

    @Test
    void pointInsideColumnRowReturnsItsId() {
        assertEquals("p.d.src#amount", CanvasHitTest.columnAt(bounds(), 10, 55));
        assertEquals("p.d.mid#total", CanvasHitTest.columnAt(bounds(), 320, 158));
    }

    @Test
    void pointInEmptySpaceReturnsNull() {
        assertNull(CanvasHitTest.columnAt(bounds(), 250, 250));
    }

    private static Map<String, NodePosition> card() {
        Map<String, NodePosition> positions = new LinkedHashMap<>();
        positions.put("n", new NodePosition("n", 100, 40, 0, 170));
        return positions;
    }

    private static CardColumns threeColumns() {
        return new CardColumns(List.of(new ColumnRef("p.d.t", "c0"), new ColumnRef("p.d.t", "c1"),
                new ColumnRef("p.d.t", "c2")), 0);
    }

    @Test
    void aPointOnTheThirdRowOfACardHitsItsColumn() {
        Map<String, Rectangle> rows = NodeCardMetrics.rowBounds(card(), 52, id -> threeColumns());
        assertEquals("p.d.t#c2", CanvasHitTest.columnAt(rows, 110, 40 + 52 + 2 * 24 + 5));
        assertEquals("p.d.t#c0", CanvasHitTest.columnAt(rows, 110, 40 + 52 + 5));
    }

    @Test
    void aPointBelowTheLastRowHitsNothing() {
        Map<String, Rectangle> rows = NodeCardMetrics.rowBounds(card(), 52, id -> threeColumns());
        assertNull(CanvasHitTest.columnAt(rows, 110, 40 + 52 + 3 * 24 + 5));
    }

    @Test
    void aPointAtTheLeftEndOfTheBottomBandOfTheHeaderHitsTheToggle() {
        assertEquals("n", CanvasHitTest.columnToggleAt(card(), 52, id -> true, 102, 40 + 52 - 9));
    }

    @Test
    void aPointAtTheRightEndOfTheBandHitsTheToggle() {
        assertEquals("n", CanvasHitTest.columnToggleAt(card(), 52, id -> true, 100 + 168, 40 + 52 - 9));
    }

    @Test
    void aPointJustAboveTheBandHitsTheNodeAndNotTheToggle() {
        assertNull(CanvasHitTest.columnToggleAt(card(), 52, id -> true, 110, 40 + 52 - 19));
        assertEquals("n", CanvasHitTest.nodeAt(card(), 52, 110, 40 + 52 - 19));
    }

    @Test
    void aPointOnAColumnRowUnderTheHeaderDoesNotHitTheToggle() {
        assertNull(CanvasHitTest.columnToggleAt(card(), 52, id -> true, 110, 40 + 52 + 5));
    }

    @Test
    void aNodeWithoutColumnsHasNoToggleBand() {
        assertNull(CanvasHitTest.columnToggleAt(card(), 52, id -> false, 110, 40 + 52 - 9));
    }

    @Test
    void theBandIsEighteenPixelsHigh() {
        assertEquals(18, NodeCardMetrics.TOGGLE_BAND_H);
        assertEquals("n", CanvasHitTest.columnToggleAt(card(), 52, id -> true, 110, 40 + 52 - 18));
    }

    @Test
    void aPointOnTheRowsOfACardHitsTheCard() {
        assertEquals("n", CanvasHitTest.cardAt(card(), 52, id -> 72, 110, 40 + 52 + 60));
        assertNull(CanvasHitTest.cardAt(card(), 52, id -> 72, 110, 40 + 52 + 80));
    }
}
