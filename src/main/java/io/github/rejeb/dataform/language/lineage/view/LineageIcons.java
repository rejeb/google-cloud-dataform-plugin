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

import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;

/**
 * The icons of the lineage views, drawn from the stroke glyphs of the UI prototype in its light
 * and dark colours.
 */
final class LineageIcons {

    static final Icon FILTERS = load("lineageFilter");
    static final Icon DIRECTION_LR = load("lineageLayoutLR");
    static final Icon DIRECTION_TB = load("lineageLayoutTB");
    static final Icon DENSITY = load("lineageDensity");
    static final Icon MINIMAP = load("lineageMinimap");
    static final Icon DETAILS = load("lineageDetails");
    static final Icon FIT = load("lineageFit");
    static final Icon ZOOM_IN = load("lineageZoomIn");
    static final Icon ZOOM_OUT = load("lineageZoomOut");
    static final Icon FOCUS = load("lineageFocus");
    static final Icon MINUS = load("lineageMinus");

    private LineageIcons() {
    }

    private static @NotNull Icon load(@NotNull String name) {
        return IconLoader.getIcon("/icons/" + name + ".svg", LineageIcons.class);
    }
}
