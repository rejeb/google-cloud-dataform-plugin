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

import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import javax.swing.UIManager;
import java.awt.Color;
import java.util.function.Supplier;

public class LineageThemeTest extends BasePlatformTestCase {

    private static void assertThemeKey(String key, Supplier<Color> colour, int lightFallback) {
        assertEquals(key, lightFallback, colour.get().getRGB() & 0xFFFFFF);
        UIManager.put(key, Color.MAGENTA);
        try {
            assertEquals(key, Color.MAGENTA.getRGB(), colour.get().getRGB());
        } finally {
            UIManager.put(key, null);
        }
    }

    public void testCanvasColoursAreThemeKeys() {
        assertThemeKey("Dataform.Lineage.NodeBackground", LineageTheme::nodeBackground, 0xFFFFFF);
        assertThemeKey("Dataform.Lineage.NodeBorder", LineageTheme::nodeBorder, 0xD3D5DB);
        assertThemeKey("Dataform.Lineage.Accent", LineageTheme::accentColor, 0x3574F0);
        assertThemeKey("Dataform.Lineage.Edge", LineageTheme::edgeColor, 0xA8ADBD);
        assertThemeKey("Dataform.Lineage.GraphDot", LineageTheme::graphDot, 0xEBECF0);
        assertThemeKey("Dataform.Lineage.SearchMatch", LineageTheme::searchMatch, 0xFFAF0F);
        assertThemeKey("Dataform.Lineage.MinimapNode", LineageTheme::minimapNode, 0xDFE1E5);
        assertThemeKey("Dataform.Lineage.SelectedSchema", LineageTheme::selectedSchema, 0x565A66);
    }

    public void testGlyphColoursAreThePrototypeThemeKeys() {
        assertThemeKey("Dataform.Lineage.Glyph.Table.Background", () -> LineageTheme.glyphBackground("table"), 0xDDF2E0);
        assertThemeKey("Dataform.Lineage.Glyph.Table.Foreground", () -> LineageTheme.glyphForeground("table"), 0x1B7A33);
        assertThemeKey("Dataform.Lineage.Glyph.Table.Background", () -> LineageTheme.glyphBackground("materialized_view"), 0xDDF2E0);
        assertThemeKey("Dataform.Lineage.Glyph.View.Background", () -> LineageTheme.glyphBackground("view"), 0xE1ECFF);
        assertThemeKey("Dataform.Lineage.Glyph.View.Foreground", () -> LineageTheme.glyphForeground("view"), 0x2F5FD0);
        assertThemeKey("Dataform.Lineage.Glyph.Incremental.Background", () -> LineageTheme.glyphBackground("incremental"), 0xF3E6F5);
        assertThemeKey("Dataform.Lineage.Glyph.Incremental.Foreground", () -> LineageTheme.glyphForeground("incremental"), 0x8E3A9E);
        assertThemeKey("Dataform.Lineage.Glyph.Operation.Background", () -> LineageTheme.glyphBackground("operation"), 0xFCEBDA);
        assertThemeKey("Dataform.Lineage.Glyph.Operation.Foreground", () -> LineageTheme.glyphForeground("operation"), 0x9A5200);
        assertThemeKey("Dataform.Lineage.Glyph.Test.Background", () -> LineageTheme.glyphBackground("test"), 0xDDF1F3);
        assertThemeKey("Dataform.Lineage.Glyph.Test.Foreground", () -> LineageTheme.glyphForeground("test"), 0x116B75);
        assertThemeKey("Dataform.Lineage.Glyph.Declaration.Background", () -> LineageTheme.glyphBackground("declaration"), 0xEBECF0);
        assertThemeKey("Dataform.Lineage.Glyph.Declaration.Foreground", () -> LineageTheme.glyphForeground("assertion"), 0x565A66);
    }

    public void testEveryActionTypeHasThePrototypeGlyph() {
        assertEquals("T", LineageTheme.glyphFor("table"));
        assertEquals("V", LineageTheme.glyphFor("view"));
        assertEquals("I", LineageTheme.glyphFor("incremental"));
        assertEquals("D", LineageTheme.glyphFor("declaration"));
        assertEquals("A", LineageTheme.glyphFor("assertion"));
        assertEquals("O", LineageTheme.glyphFor("operation"));
        assertEquals("O", LineageTheme.glyphFor("operations"));
        assertEquals("M", LineageTheme.glyphFor("materialized_view"));
        assertEquals("U", LineageTheme.glyphFor("test"));
    }
}
