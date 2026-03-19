/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.rejeb.dataform.language.gcp.execution.workflow.runconfig;

import com.intellij.execution.configurations.ConfigurationTypeBase;
import com.intellij.openapi.util.NotNullLazyValue;
import io.github.rejeb.dataform.language.DataformIcons;

public final class DataformWorkflowConfigurationType extends ConfigurationTypeBase {

    public static final String ID = "DataformWorkflowRunConfiguration";

    public DataformWorkflowConfigurationType() {
        super(
                ID,
                "Dataform Workflow",
                "Run a Dataform workflow on GCP",
                NotNullLazyValue.createValue(() -> DataformIcons.FILE)
        );
        addFactory(new DataformWorkflowConfigurationFactory(this));
    }
}
