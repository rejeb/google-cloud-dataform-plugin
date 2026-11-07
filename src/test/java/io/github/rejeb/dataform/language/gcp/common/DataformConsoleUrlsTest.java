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
package io.github.rejeb.dataform.language.gcp.common;

import io.github.rejeb.dataform.language.gcp.settings.DataformRepositoryConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DataformConsoleUrlsTest {

    private static final DataformRepositoryConfig SALES =
            new DataformRepositoryConfig("sales-id", "Sales", "my-gcp-project", "sales", "europe-west1");
    private static final DataformRepositoryConfig USER_PURCHASE =
            new DataformRepositoryConfig("purchase-id", "User purchase", "test-dataform-489602", "user_purchase", "us-central1");

    @Test
    void aWorkspaceOpensItsDevelopmentPage() {
        assertEquals("https://console.cloud.google.com/bigquery/dataform/locations/europe-west1/repositories/sales"
                        + "/workspaces/dev?project=my-gcp-project",
                DataformConsoleUrls.of(SALES, "dev"));
    }

    @Test
    void theMainBranchOpensTheRepository() {
        assertEquals("https://console.cloud.google.com/bigquery/dataform/locations/europe-west1/repositories/sales"
                        + "/details/workspaces?project=my-gcp-project",
                DataformConsoleUrls.of(SALES, null));
    }

    @Test
    void aFileOpensInItsWorkspaceWithItsPathAsOneSegment() {
        assertEquals("https://console.cloud.google.com/bigquery/dataform/locations/us-central1/repositories/user_purchase"
                        + "/workspaces/demo/files/definitions%2Fbronze%2Fbronze_order_items.sqlx?project=test-dataform-489602",
                DataformConsoleUrls.ofFile(USER_PURCHASE, "demo", "definitions/bronze/bronze_order_items.sqlx"));
    }

    @Test
    void aSpaceInAFilePathIsEscapedForAPath() {
        assertEquals("https://console.cloud.google.com/bigquery/dataform/locations/europe-west1/repositories/sales"
                        + "/workspaces/dev/files/includes%2Fmy%20helpers.js?project=my-gcp-project",
                DataformConsoleUrls.ofFile(SALES, "dev", "includes/my helpers.js"));
    }
}
