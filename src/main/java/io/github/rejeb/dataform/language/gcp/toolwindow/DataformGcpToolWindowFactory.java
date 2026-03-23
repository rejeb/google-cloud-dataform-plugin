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
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import io.github.rejeb.dataform.language.gcp.toolwindow.action.CreateWorkspaceAction;
import io.github.rejeb.dataform.language.gcp.toolwindow.action.ManageRepositoriesAction;
import io.github.rejeb.dataform.language.gcp.toolwindow.action.RefreshAction;
import io.github.rejeb.dataform.language.gcp.toolwindow.dispatcher.GcpPanelActionDispatcher;
import io.github.rejeb.dataform.language.gcp.toolwindow.dispatcher.GcpPanelActionDispatcherImpl;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class DataformGcpToolWindowFactory implements ToolWindowFactory {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        DataformGcpPanel panel = new DataformGcpPanel(project);

        Content content = ContentFactory.getInstance()
                .createContent(panel, "", false);
        toolWindow.getContentManager().addContent(content);

        toolWindow.setTitleActions(List.of(
                new RefreshAction(panel.getDispatcher()),
                new CreateWorkspaceAction(panel.getDispatcher()),
                new ManageRepositoriesAction(project, panel::refresh)
        ));
    }
}

