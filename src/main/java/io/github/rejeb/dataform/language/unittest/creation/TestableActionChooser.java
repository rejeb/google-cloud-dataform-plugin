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
package io.github.rejeb.dataform.language.unittest.creation;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.CheckBoxList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import io.github.rejeb.dataform.language.compilation.model.CompiledTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class TestableActionChooser extends DialogWrapper {

    private final CheckBoxList<CompiledTable> list = new CheckBoxList<>();
    private final List<CompiledTable> actions;

    private TestableActionChooser(@NotNull Project project,
                                  @NotNull List<CompiledTable> actions,
                                  @NotNull Set<CompiledTable> existing) {
        super(project);
        this.actions = actions;
        for (CompiledTable action : actions) {
            boolean exists = existing.contains(action);
            list.addItem(action, label(action, exists), !exists);
        }
        setTitle("Create Dataform Tests");
        setOKButtonText("Create");
        init();
    }

    /**
     * Asks which of the given actions get a test, with the actions that already have one unchecked.
     * Returns the chosen actions, empty when the dialog is cancelled.
     */
    @NotNull
    public static List<CompiledTable> choose(@NotNull Project project,
                                             @NotNull List<CompiledTable> actions,
                                             @NotNull Set<CompiledTable> existing) {
        TestableActionChooser chooser = new TestableActionChooser(project, actions, existing);
        if (!chooser.showAndGet()) {
            return List.of();
        }
        List<CompiledTable> chosen = new ArrayList<>();
        for (CompiledTable action : chooser.actions) {
            if (chooser.list.isItemSelected(action)) {
                chosen.add(action);
            }
        }
        return chosen;
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JBScrollPane pane = new JBScrollPane(list);
        pane.setPreferredSize(JBUI.size(420, 220));
        return pane;
    }

    private static String label(@NotNull CompiledTable action, boolean exists) {
        String text = action.getTarget().getName()
                + " (" + action.getActionKind() + ", " + action.getTarget().getSchema() + ")";
        return exists ? text + " — test exists" : text;
    }
}
