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
package io.github.rejeb.dataform.language.lineage.dataplex;

import io.github.rejeb.dataform.language.lineage.graph.LineageNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Names of the entities of a recorded lineage. Knowledge Catalog names an entity by a fully
 * qualified name: {@code bigquery:project.dataset.table} for a BigQuery table, another scheme for
 * anything else, such as {@code gs://bucket/path} for Cloud Storage.
 */
public final class EntityNames {

    private static final String BIGQUERY = "bigquery:";
    private static final String CLOUD_STORAGE = "gs://";
    private static final String NODE_PREFIX = "node:";

    private EntityNames() {
    }

    /**
     * @param fullName the full name of a BigQuery table, {@code project.dataset.table}
     * @return its fully qualified name
     */
    public static @NotNull String fqnOf(@NotNull String fullName) {
        return BIGQUERY + fullName;
    }

    /**
     * @return the full name of the BigQuery table, or {@code null} when the entity is not one
     */
    public static @Nullable String fullNameOf(@NotNull String fqn) {
        return fqn.startsWith(BIGQUERY) ? fqn.substring(BIGQUERY.length()) : null;
    }

    /**
     * @return the id of the graph node of the entity: the id of the Dataform table for a BigQuery
     * table, so a table has the same node in both sources
     */
    public static @NotNull String nodeIdOf(@NotNull String fqn) {
        String fullName = fullNameOf(fqn);
        return LineageNode.idOf(fullName != null ? fullName : fqn);
    }

    /**
     * @param nodeId a node id made by {@link #nodeIdOf}
     * @return the fully qualified name of the entity of that node
     */
    public static @NotNull String fqnOfNodeId(@NotNull String nodeId) {
        String name = nodeId.startsWith(NODE_PREFIX) ? nodeId.substring(NODE_PREFIX.length()) : nodeId;
        int colon = name.indexOf(':');
        boolean hasScheme = name.contains("://") || colon > 0 && name.lastIndexOf('.', colon) < 0;
        return hasScheme ? name : fqnOf(name);
    }

    /**
     * @return the system the entity lives in, as shown to the user; the scheme itself when it is
     * not a known one
     */
    public static @NotNull String kindOf(@NotNull String fqn) {
        if (fqn.startsWith(BIGQUERY)) return "BigQuery";
        if (fqn.startsWith(CLOUD_STORAGE)) return "Cloud Storage";
        int colon = fqn.indexOf(':');
        return colon > 0 ? fqn.substring(0, colon) : "Other";
    }

    /**
     * @return the name shown on the node: the table for BigQuery, the path inside the bucket for
     * Cloud Storage, the part after the scheme otherwise
     */
    public static @NotNull String shortNameOf(@NotNull String fqn) {
        String fullName = fullNameOf(fqn);
        if (fullName != null) {
            int tableStart = tableStart(fullName);
            return tableStart < 0 ? fullName : fullName.substring(tableStart);
        }
        if (fqn.startsWith(CLOUD_STORAGE)) {
            String location = fqn.substring(CLOUD_STORAGE.length());
            int slash = location.indexOf('/');
            return slash < 0 || slash == location.length() - 1 ? location : location.substring(slash + 1);
        }
        int colon = fqn.indexOf(':');
        return colon > 0 && colon < fqn.length() - 1 ? fqn.substring(colon + 1) : fqn;
    }

    /**
     * @return what the entity sits in, shown under its name: {@code project.dataset} for a BigQuery
     * table, the kind otherwise
     */
    public static @NotNull String containerOf(@NotNull String fqn) {
        String fullName = fullNameOf(fqn);
        if (fullName != null) {
            int tableStart = tableStart(fullName);
            if (tableStart > 0) return fullName.substring(0, tableStart - 1);
        }
        return kindOf(fqn);
    }

    private static int tableStart(@NotNull String fullName) {
        int lastDot = fullName.lastIndexOf('.');
        if (lastDot <= 0 || fullName.lastIndexOf('.', lastDot - 1) < 0) return -1;
        return lastDot + 1;
    }
}
