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

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.datacatalog.lineage.v1.BatchSearchLinkProcessesRequest;
import com.google.cloud.datacatalog.lineage.v1.EntityReference;
import com.google.cloud.datacatalog.lineage.v1.LineageClient;
import com.google.cloud.datacatalog.lineage.v1.LineageSettings;
import com.google.cloud.datacatalog.lineage.v1.Link;
import com.google.cloud.datacatalog.lineage.v1.Process;
import com.google.cloud.datacatalog.lineage.v1.ProcessLinks;
import com.google.cloud.datacatalog.lineage.v1.Run;
import com.google.cloud.datacatalog.lineage.v1.SearchLinksRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Opt-in probe of the Data Lineage API on a real project. It never runs in the normal test suite:
 * it needs {@code DATAPLEX_PROBE_PROJECT}, {@code DATAPLEX_PROBE_LOCATION} and
 * {@code DATAPLEX_PROBE_TABLE} ({@code project.dataset.table}), optionally
 * {@code DATAPLEX_PROBE_COLUMN} and {@code DATAPLEX_PROBE_TOKEN} (an OAuth access token used instead
 * of the application default credentials).
 */
@EnabledIfEnvironmentVariable(named = "DATAPLEX_PROBE_TABLE", matches = ".+")
class DataLineageProbe {

    @Test
    void printsWhatTheApiRecordedForTheTable() throws Exception {
        String project = System.getenv("DATAPLEX_PROBE_PROJECT");
        String location = System.getenv("DATAPLEX_PROBE_LOCATION");
        String fqn = "bigquery:" + System.getenv("DATAPLEX_PROBE_TABLE");
        String column = System.getenv("DATAPLEX_PROBE_COLUMN");
        String parent = "projects/" + project + "/locations/" + location;
        try (LineageClient client = LineageClient.create(settings(project))) {
            List<Link> incoming = links(client, parent, null, entity(fqn, null));
            List<Link> outgoing = links(client, parent, entity(fqn, null), null);
            print("links into " + fqn, incoming);
            print("links out of " + fqn, outgoing);
            if (column != null && !column.isBlank()) {
                print("column links into " + fqn + "#" + column, links(client, parent, null, entity(fqn, column)));
                print("column links out of " + fqn + "#" + column, links(client, parent, entity(fqn, column), null));
            }
            Set<String> linkNames = new LinkedHashSet<>();
            incoming.stream().limit(5).forEach(l -> linkNames.add(l.getName()));
            outgoing.stream().limit(5).forEach(l -> linkNames.add(l.getName()));
            if (linkNames.isEmpty()) return;
            String firstProcess = null;
            for (ProcessLinks processLinks : client.batchSearchLinkProcesses(BatchSearchLinkProcessesRequest.newBuilder()
                    .setParent(parent).addAllLinks(linkNames).setPageSize(100).build()).iterateAll()) {
                Process process = client.getProcess(processLinks.getProcess());
                System.out.println("process " + process.getName() + " | display=" + process.getDisplayName()
                        + " | origin=" + process.getOrigin().getSourceType() + " " + process.getOrigin().getName()
                        + " | attributes=" + process.getAttributesMap() + " | links=" + processLinks.getLinksCount());
                if (firstProcess == null) firstProcess = process.getName();
            }
            if (firstProcess == null) return;
            int shown = 0;
            for (Run run : client.listRuns(firstProcess).iterateAll()) {
                System.out.println("run " + run.getName() + " | " + run.getState() + " | " + run.getStartTime().getSeconds()
                        + " -> " + run.getEndTime().getSeconds() + " | attributes=" + run.getAttributesMap());
                if (++shown == 3) break;
            }
        }
    }

    private static LineageSettings settings(String project) throws Exception {
        String token = System.getenv("DATAPLEX_PROBE_TOKEN");
        GoogleCredentials credentials = token != null && !token.isBlank()
                ? GoogleCredentials.create(new AccessToken(token, null))
                : GoogleCredentials.getApplicationDefault();
        return LineageSettings.newHttpJsonBuilder()
                .setCredentialsProvider(FixedCredentialsProvider.create(credentials.createWithQuotaProject(project)))
                .build();
    }

    private static EntityReference entity(String fqn, String column) {
        EntityReference.Builder builder = EntityReference.newBuilder().setFullyQualifiedName(fqn);
        if (column != null) builder.addField(column);
        return builder.build();
    }

    private static List<Link> links(LineageClient client, String parent, EntityReference source, EntityReference target) {
        SearchLinksRequest.Builder request = SearchLinksRequest.newBuilder().setParent(parent).setPageSize(100);
        if (source != null) request.setSource(source);
        if (target != null) request.setTarget(target);
        List<Link> result = new ArrayList<>();
        client.searchLinks(request.build()).iterateAll().forEach(result::add);
        return result;
    }

    private static void print(String title, List<Link> links) {
        System.out.println("== " + title + ": " + links.size());
        for (Link link : links) {
            System.out.println("  " + link.getSource().getFullyQualifiedName() + link.getSource().getFieldList()
                    + " -> " + link.getTarget().getFullyQualifiedName() + link.getTarget().getFieldList()
                    + " | " + link.getStartTime().getSeconds() + " .. " + link.getEndTime().getSeconds()
                    + " | " + link.getDependencyInfoList().stream().map(d -> d.getDependencyType().name()).toList()
                    + " | " + link.getName());
        }
    }
}
