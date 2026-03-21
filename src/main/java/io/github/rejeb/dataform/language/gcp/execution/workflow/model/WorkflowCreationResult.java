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
package io.github.rejeb.dataform.language.gcp.execution.workflow.model;

import org.jetbrains.annotations.NotNull;

/**
 * Result of a workflow run creation, carrying both the invocation resource name
 * and the workspace name used for compilation.
 */
public record WorkflowCreationResult(
        @NotNull String invocationName,
        @NotNull String workspaceFullName
) {}