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
import com.intellij.ui.icons.CachedImageIcon;

import javax.swing.Icon;
import java.util.Map;

public class LineageIconsTest extends BasePlatformTestCase {

    public void testTheIconsAreThePrototypeSvgsAt16Pixels() {
        Map<String, Icon> icons = Map.ofEntries(
                Map.entry("lineageFilter", LineageIcons.FILTERS),
                Map.entry("lineageLayoutLR", LineageIcons.DIRECTION_LR),
                Map.entry("lineageLayoutTB", LineageIcons.DIRECTION_TB),
                Map.entry("lineageDensity", LineageIcons.DENSITY),
                Map.entry("lineageMinimap", LineageIcons.MINIMAP),
                Map.entry("lineageDetails", LineageIcons.DETAILS),
                Map.entry("lineageFit", LineageIcons.FIT),
                Map.entry("lineageZoomIn", LineageIcons.ZOOM_IN),
                Map.entry("lineageZoomOut", LineageIcons.ZOOM_OUT),
                Map.entry("lineageFocus", LineageIcons.FOCUS),
                Map.entry("lineageMinus", LineageIcons.MINUS));
        icons.forEach((name, icon) -> {
            assertInstanceOf(icon, CachedImageIcon.class);
            assertTrue(name, ((CachedImageIcon) icon).getOriginalPath().endsWith("/icons/" + name + ".svg"));
            assertEquals(name, 16, icon.getIconWidth());
            assertEquals(name, 16, icon.getIconHeight());
        });
    }
}
