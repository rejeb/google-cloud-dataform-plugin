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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import io.github.rejeb.dataform.language.util.Utils;
import org.jetbrains.annotations.NotNull;


public class DataformGcpToolWindowFactory implements ToolWindowFactory {

    /**
     * Keeps the Dataform Tools tool window hidden in projects that are not Dataform projects.
     */
    @Override
    public boolean shouldBeAvailable(@NotNull Project project) {
        return Utils.isDataformProject(project);
    }

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        DataformGcpPanel panel = new DataformGcpPanel(project);
        Disposer.register(toolWindow.getDisposable(), panel);

        ContentFactory factory = ContentFactory.getInstance();
        ContentManager contents = toolWindow.getContentManager();
        contents.addContent(factory.createContent(panel.filesPanel(), "Files", false));
        Content changes = factory.createContent(panel.changesPanel(), ChangesTabTitle.of(0), false);
        contents.addContent(changes);
        panel.setChangesCountListener(count -> changes.setDisplayName(ChangesTabTitle.of(count)));

        toolWindow.setTitleActions(panel.titleActions());
        if (toolWindow instanceof ToolWindowEx window) {
            window.setAdditionalGearActions(panel.gearActions());
        }
    }
}
