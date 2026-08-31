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
package io.github.rejeb.dataform.language.refactoring.column.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import io.github.rejeb.dataform.language.refactoring.column.plan.ColumnRenamePlan;
import io.github.rejeb.dataform.language.refactoring.column.plan.StarBoundary;
import io.github.rejeb.dataform.language.refactoring.column.plan.StarResolution;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import java.awt.BorderLayout;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Asks the user, once, what to do about the columns a star produces.
 *
 * <p>Expanding the star gives the column a declaration to rename, at the price of freezing the
 * column list of that action. Declaring the new name in the file of the caret keeps every file
 * upstream untouched. Neither is right in general, so neither is chosen for the user.</p>
 */
public final class StarResolutionDialog implements StarResolutionChooser {

    @Override
    public @NotNull StarResolution choose(@NotNull Project project,
                                          @NotNull ColumnRenamePlan plan) {
        if (ApplicationManager.getApplication().isUnitTestMode()) {
            return StarResolution.CANCEL;
        }
        Dialog dialog = new Dialog(project, plan);
        return dialog.showAndGet() ? dialog.choice() : StarResolution.CANCEL;
    }

    private static final class Dialog extends DialogWrapper {

        private final ColumnRenamePlan plan;
        private final JRadioButton expand = new JRadioButton("Expand the star into the column list");
        private final JRadioButton alias;

        private Dialog(@NotNull Project project, @NotNull ColumnRenamePlan plan) {
            super(project, false);
            this.plan = plan;
            this.alias = new JRadioButton("Declare \"" + plan.newName() + "\" in "
                    + plan.subject().hostFile().getName() + " and leave the sources unchanged");
            setTitle("Rename Column Produced by a Star");
            init();
        }

        private @NotNull StarResolution choice() {
            return expand.isSelected() ? StarResolution.EXPAND : StarResolution.ALIAS_IN_CURRENT_FILE;
        }

        @Override
        protected @Nullable JComponent createCenterPanel() {
            JPanel panel = new JPanel(new BorderLayout());
            panel.add(new JLabel("<html>" + explanation() + "</html>"), BorderLayout.NORTH);

            JPanel options = new JPanel();
            options.setLayout(new BoxLayout(options, BoxLayout.Y_AXIS));
            boolean expandable = plan.starBoundaries().stream().anyMatch(StarBoundary::canExpand);
            boolean aliasable = plan.subject().declaresColumn();
            expand.setEnabled(expandable);
            alias.setEnabled(aliasable);
            expand.setSelected(expandable || !aliasable);
            alias.setSelected(!expandable && aliasable);

            ButtonGroup group = new ButtonGroup();
            group.add(expand);
            group.add(alias);
            options.add(expand);
            options.add(new JLabel("<html><i>The action stops picking up new columns of its "
                    + "sources on its own.</i></html>"));
            options.add(alias);
            options.add(new JLabel("<html><i>Everything upstream keeps the old name.</i></html>"));
            panel.add(options, BorderLayout.CENTER);
            return panel;
        }

        private @NotNull String explanation() {
            Set<String> files = new LinkedHashSet<>();
            for (StarBoundary boundary : plan.starBoundaries()) {
                files.add(boundary.file() == null ? boundary.column().tableFullName()
                        : boundary.file().getName());
            }
            return "\"" + plan.subject().oldName() + "\" is produced by a star in "
                    + String.join(", ", files)
                    + ", so there is no name to rewrite there.<br><br>";
        }
    }
}
