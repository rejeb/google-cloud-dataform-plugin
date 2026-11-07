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
package io.github.rejeb.dataform.language.gcp.toolwindow;

import com.intellij.icons.AllIcons;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import io.github.rejeb.dataform.language.gcp.settings.DataformRepositoryConfig;
import io.github.rejeb.dataform.language.ui.UiText;
import org.jetbrains.annotations.NotNull;

import javax.swing.JList;

/**
 * Shows a Dataform repository as its label followed by its project and location, grayed.
 */
public class RepositoryListCellRenderer extends ColoredListCellRenderer<DataformRepositoryConfig> {

    @Override
    protected void customizeCellRenderer(@NotNull JList<? extends DataformRepositoryConfig> list,
                                         DataformRepositoryConfig value, int index, boolean selected, boolean hasFocus) {
        if (value == null) {
            return;
        }
        setIcon(AllIcons.Nodes.DataSchema);
        append(value.displayName());
        String place = UiText.dot(value.projectId(), value.location());
        if (!place.isEmpty()) {
            append("  " + place, SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }
    }
}
