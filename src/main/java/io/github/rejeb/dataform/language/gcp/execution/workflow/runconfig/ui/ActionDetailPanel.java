/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.rejeb.dataform.language.gcp.execution.workflow.runconfig.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.components.JBTabbedPane;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.InvocationActionResult;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.WorkflowInvocationProgress;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class ActionDetailPanel extends JPanel {

    private final ActionSummaryPanel summaryPanel;
    private final ActionDetailsTabPanel detailsPanel;
    private InvocationActionResult currentAction;
    public ActionDetailPanel(@NotNull Project project) {
        super(new BorderLayout());
        summaryPanel = new ActionSummaryPanel(project);
        detailsPanel = new ActionDetailsTabPanel(project);
        Disposer.register(project, detailsPanel);
        JBTabbedPane tabs = new JBTabbedPane();
        tabs.addTab("Summary", summaryPanel);
        tabs.addTab("Details", detailsPanel);
        add(tabs, BorderLayout.CENTER);
    }

    public void show(@NotNull WorkflowInvocationProgress progress,
                     @NotNull InvocationActionResult action) {
        this.currentAction = action;
        summaryPanel.show(progress, action);
        detailsPanel.load(action);
    }

    public void release() {
        summaryPanel.release();
    }
}