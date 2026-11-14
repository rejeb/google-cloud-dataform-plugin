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

import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;

/**
 * The icons of the lineage views: the bundled platform icon where the platform has one for the
 * same action, otherwise a stroke glyph of the UI prototype in its light and dark colours.
 */
final class LineageIcons {

    static final Icon FILTERS = AllIcons.General.Filter;
    static final Icon DIRECTION_LR = load("lineageLayoutLR");
    static final Icon DIRECTION_TB = load("lineageLayoutTB");
    static final Icon DENSITY = load("lineageDensity");
    static final Icon MINIMAP = AllIcons.Actions.Minimap;
    static final Icon DETAILS = AllIcons.Actions.PreviewDetails;
    static final Icon FIT = AllIcons.General.FitContent;
    static final Icon ZOOM_IN = AllIcons.Graph.ZoomIn;
    static final Icon ZOOM_OUT = AllIcons.Graph.ZoomOut;
    static final Icon FOCUS = load("lineageFocus");
    static final Icon MINUS = AllIcons.General.HideToolWindow;

    private LineageIcons() {
    }

    private static @NotNull Icon load(@NotNull String name) {
        return IconLoader.getIcon("/icons/" + name + ".svg", LineageIcons.class);
    }
}
