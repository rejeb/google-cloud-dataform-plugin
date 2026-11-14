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
import org.junit.jupiter.api.Test;

import static io.github.rejeb.dataform.language.lineage.dataplex.EntityNames.containerOf;
import static io.github.rejeb.dataform.language.lineage.dataplex.EntityNames.fqnOf;
import static io.github.rejeb.dataform.language.lineage.dataplex.EntityNames.fullNameOf;
import static io.github.rejeb.dataform.language.lineage.dataplex.EntityNames.kindOf;
import static io.github.rejeb.dataform.language.lineage.dataplex.EntityNames.nodeIdOf;
import static io.github.rejeb.dataform.language.lineage.dataplex.EntityNames.shortNameOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EntityNamesTest {

    @Test
    void aBigQueryTableNameGoesToItsFullyQualifiedNameAndBack() {
        assertEquals("bigquery:p.d.t", fqnOf("p.d.t"));
        assertEquals("p.d.t", fullNameOf("bigquery:p.d.t"));
    }

    @Test
    void aNameOutsideBigQueryHasNoFullName() {
        assertNull(fullNameOf("gs://bucket/orders/*.csv"));
        assertNull(fullNameOf("no-scheme"));
    }

    @Test
    void aBigQueryEntityHasTheNodeIdOfItsDataformTable() {
        assertEquals(LineageNode.idOf("p.d.t"), nodeIdOf("bigquery:p.d.t"));
    }

    @Test
    void anotherEntityHasANodeIdOfItsOwn() {
        assertEquals("node:gs://bucket/orders/*.csv", nodeIdOf("gs://bucket/orders/*.csv"));
    }

    @Test
    void theKindNamesTheSystemOfTheEntity() {
        assertEquals("BigQuery", kindOf("bigquery:p.d.t"));
        assertEquals("Cloud Storage", kindOf("gs://b/x"));
    }

    @Test
    void anUnknownSchemeIsItsOwnKind() {
        assertEquals("custom", kindOf("custom:anything"));
        assertEquals("Other", kindOf("no-scheme"));
    }

    @Test
    void aBigQueryEntityIsNamedByItsTableInItsProjectAndDataset() {
        assertEquals("t", shortNameOf("bigquery:p.d.t"));
        assertEquals("p.d", containerOf("bigquery:p.d.t"));
    }

    @Test
    void wildcardAndPartitionSuffixesStayInTheTableName() {
        assertEquals("events_*", shortNameOf("bigquery:p.d.events_*"));
        assertEquals("t$20261111", shortNameOf("bigquery:p.d.t$20261111"));
    }

    @Test
    void aCloudStorageEntityIsNamedByItsPath() {
        assertEquals("orders/*.csv", shortNameOf("gs://bucket/orders/*.csv"));
        assertEquals("Cloud Storage", containerOf("gs://bucket/orders/*.csv"));
        assertEquals("bucket", shortNameOf("gs://bucket"));
    }

    @Test
    void aNameWithoutSchemeIsShownRaw() {
        assertEquals("no-scheme", shortNameOf("no-scheme"));
        assertEquals("anything", shortNameOf("custom:anything"));
        assertEquals("custom", containerOf("custom:anything"));
    }

    @Test
    void aBigQueryNameWithFewerThanThreePartsIsKeptWhole() {
        assertEquals("d.t", shortNameOf("bigquery:d.t"));
        assertEquals("BigQuery", containerOf("bigquery:d.t"));
    }

    @Test
    void aProjectIdWithADomainKeepsItsDotsInTheContainer() {
        assertEquals("t", shortNameOf("bigquery:example.com:p.d.t"));
        assertEquals("example.com:p.d", containerOf("bigquery:example.com:p.d.t"));
    }

    @Test
    void aNodeIdGoesBackToItsFullyQualifiedName() {
        assertEquals("bigquery:p.d.t", EntityNames.fqnOfNodeId(nodeIdOf("bigquery:p.d.t")));
        assertEquals("gs://bucket/orders/*.csv", EntityNames.fqnOfNodeId(nodeIdOf("gs://bucket/orders/*.csv")));
        assertEquals("custom:anything", EntityNames.fqnOfNodeId(nodeIdOf("custom:anything")));
        assertEquals("bigquery:example.com:p.d.t", EntityNames.fqnOfNodeId(nodeIdOf("bigquery:example.com:p.d.t")));
    }
}
