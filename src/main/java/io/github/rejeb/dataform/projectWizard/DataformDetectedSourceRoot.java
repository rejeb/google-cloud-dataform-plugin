/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
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
package io.github.rejeb.dataform.projectWizard;

import com.intellij.ide.util.projectWizard.importSources.DetectedSourceRoot;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class DataformDetectedSourceRoot extends DetectedSourceRoot {
    public DataformDetectedSourceRoot(File directory, @Nullable String packagePrefix) {
        super(directory, packagePrefix);
    }

    @Override
    public @NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String getRootTypeName() {
        return DataformModuleType.ID;
    }
}
