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
package io.github.rejeb.dataform.language.unittest.execution.runconfig;

import com.intellij.execution.configurations.ConfigurationTypeBase;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.NotNullLazyValue;

public final class DataformTestConfigurationType extends ConfigurationTypeBase {

    public static final String ID = "DataformUnitTestRunConfiguration";

    public DataformTestConfigurationType() {
        super(ID, "Dataform Unit Tests", "Run Dataform unit tests on BigQuery",
                NotNullLazyValue.createValue(() -> AllIcons.Nodes.Test));
        addFactory(new DataformTestConfigurationFactory(this));
    }
}
