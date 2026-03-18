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
package io.github.rejeb.dataform.language.gcp.execution.serviceview;

import com.intellij.execution.services.ServiceViewDescriptor;
import com.intellij.openapi.project.Project;
import io.github.rejeb.dataform.language.gcp.bigquery.BigQueryJobResult;
import org.jetbrains.annotations.NotNull;

public final class QueryResultNode {

    private final BigQueryJobResult result;

    public QueryResultNode(@NotNull BigQueryJobResult result) {
        this.result = result;
    }

    public @NotNull BigQueryJobResult getResult() {
        return result;
    }

    public @NotNull ServiceViewDescriptor getViewDescriptor(@NotNull Project project) {
        return new DataformServiceViewDescriptor(project, result);
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof QueryResultNode other)) return false;
        return result.tableName().equals(other.result.tableName());
    }

    @Override
    public int hashCode() {
        return result.tableName().hashCode();
    }
}
