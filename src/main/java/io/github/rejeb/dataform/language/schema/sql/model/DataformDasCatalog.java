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
package io.github.rejeb.dataform.language.schema.sql.model;

import com.intellij.database.model.DasNamespace;
import com.intellij.database.model.DasObject;
import com.intellij.database.model.ObjectKind;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DataformDasCatalog implements DasNamespace {
    private final String myProject;
    private final List<DataformDasSchema> mySchemas;

    public DataformDasCatalog(String project,
                              Map<String, List<Map.Entry<String, List<ColumnInfo>>>> datasets) {
        this.myProject = project;
        this.mySchemas = new ArrayList<>();
        for (Map.Entry<String, List<Map.Entry<String, List<ColumnInfo>>>> e : datasets.entrySet()) {
            mySchemas.add(new DataformDasSchema(this, e.getKey(), e.getValue()));
        }
    }

    @Override
    public @NotNull String getName() {
        return myProject;
    }

    @Override
    public @NotNull ObjectKind getKind() {
        return ObjectKind.DATABASE;
    }

    @Override
    public boolean isQuoted() {
        return false;
    }

    @Override
    public @NotNull JBIterable<? extends DasObject> getDasChildren(@Nullable ObjectKind kind) {
        if (kind == ObjectKind.SCHEMA) {
            return JBIterable.from(mySchemas);
        }
        return JBIterable.empty();
    }
}
