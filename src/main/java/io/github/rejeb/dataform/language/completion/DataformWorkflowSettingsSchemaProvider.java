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
package io.github.rejeb.dataform.language.completion;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider;
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory;
import com.jetbrains.jsonSchema.extension.SchemaType;
import io.github.rejeb.dataform.language.schema.DataformJsonSchemaGenerator;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class DataformWorkflowSettingsSchemaProvider implements JsonSchemaFileProvider {
    private static final String SCHEMA_FILE_NAME = "dataform-workflow-settings-schema.json";
    private final Project project;

    public DataformWorkflowSettingsSchemaProvider(Project project) {
        this.project = project;
    }

    @Override
    public boolean isAvailable(@NotNull VirtualFile virtualFile) {
        return virtualFile.getName().endsWith("workflow_settings.yaml");
    }

    @Override
    public @NotNull @Nls String getName() {
        return "Dataform Workflow Settings";
    }

    @Override
    public @Nullable VirtualFile getSchemaFile() {
        DataformJsonSchemaGenerator generator = project.getService(DataformJsonSchemaGenerator.class);
        Optional<VirtualFile> schema = generator.generateWorkflowSettingsSchema();
        return schema.orElse(JsonSchemaProviderFactory.getResourceFile(getClass(), "/dataform/" + SCHEMA_FILE_NAME));
    }

    @Override
    public @NotNull SchemaType getSchemaType() {
        return SchemaType.userSchema;
    }

}
