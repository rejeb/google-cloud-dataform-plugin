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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceAccountListerTest {

    private static final String RUNNER = "dataform-runner@p.iam.gserviceaccount.com";
    private static final String COMPUTE = "1234-compute@developer.gserviceaccount.com";
    private static final String DATAFORM_AGENT = "service-1234@gcp-sa-dataform.iam.gserviceaccount.com";
    private static final String CLOUD_SERVICES = "1234@cloudservices.gserviceaccount.com";

    @Test
    void anAccountsPageGivesItsEmailsAndNextPageToken() {
        ServiceAccountResponseParser.AccountsPage page = ServiceAccountResponseParser.accountsPage("""
                {"accounts":[{"email":"%s"},{"name":"no-email"},{"email":" "},{"email":"%s"}],"nextPageToken":"next"}
                """.formatted(RUNNER, COMPUTE));

        assertEquals(List.of(RUNNER, COMPUTE), page.emails());
        assertEquals("next", page.nextPageToken());
    }

    @Test
    void anAccountsPageWithoutAccountsIsEmpty() {
        ServiceAccountResponseParser.AccountsPage page = ServiceAccountResponseParser.accountsPage("{}");

        assertEquals(List.of(), page.emails());
        assertNull(page.nextPageToken());
    }

    @Test
    void thePolicyGivesEveryServiceAccountGrantedARoleOnce() {
        List<String> accounts = ServiceAccountResponseParser.policyServiceAccounts("""
                {"version":1,"etag":"x","bindings":[
                  {"role":"roles/dataform.serviceAgent","members":["serviceAccount:%s"]},
                  {"role":"roles/editor","members":[
                    "serviceAccount:%s","serviceAccount:%s","user:someone@example.com",
                    "group:team@example.com","deleted:serviceAccount:gone@p.iam.gserviceaccount.com?uid=1",
                    "serviceAccount:p.svc.id.goog[default/ksa]"]},
                  {"role":"roles/bigquery.admin","members":["serviceAccount:%s"]}
                ]}
                """.formatted(DATAFORM_AGENT, CLOUD_SERVICES, RUNNER, DATAFORM_AGENT));

        assertEquals(List.of(CLOUD_SERVICES, RUNNER, DATAFORM_AGENT), accounts);
    }

    @Test
    void aPolicyWithoutBindingsGivesNoServiceAccount() {
        assertEquals(List.of(), ServiceAccountResponseParser.policyServiceAccounts("{\"etag\":\"x\"}"));
    }

    @Test
    void googleProvidedAccountsFollowTheProjectAccountsWithoutDuplicates() {
        ServiceAccountLister.Result merged = ServiceAccountLister.Result.merge(
                new ServiceAccountLister.Result(List.of(RUNNER, COMPUTE), false),
                new ServiceAccountLister.Result(List.of(CLOUD_SERVICES, RUNNER, DATAFORM_AGENT), false));

        assertEquals(List.of(RUNNER, COMPUTE, CLOUD_SERVICES, DATAFORM_AGENT), merged.emails());
        assertFalse(merged.permissionDenied());
    }

    @Test
    void thePolicyStillListsAccountsWhenProjectAccountsAreDenied() {
        ServiceAccountLister.Result merged = ServiceAccountLister.Result.merge(
                ServiceAccountLister.Result.denied(),
                new ServiceAccountLister.Result(List.of(DATAFORM_AGENT), false));

        assertEquals(List.of(DATAFORM_AGENT), merged.emails());
        assertFalse(merged.permissionDenied());
    }

    @Test
    void projectAccountsAreKeptWhenThePolicyCannotBeRead() {
        ServiceAccountLister.Result merged = ServiceAccountLister.Result.merge(
                new ServiceAccountLister.Result(List.of(RUNNER), false),
                ServiceAccountLister.Result.denied());

        assertEquals(List.of(RUNNER), merged.emails());
        assertFalse(merged.permissionDenied());
    }

    @Test
    void theListingIsDeniedOnlyWhenBothSourcesAreDenied() {
        assertTrue(ServiceAccountLister.Result.merge(
                ServiceAccountLister.Result.denied(), ServiceAccountLister.Result.denied()).permissionDenied());
        assertFalse(ServiceAccountLister.Result.merge(
                ServiceAccountLister.Result.denied(), ServiceAccountLister.Result.unavailable()).permissionDenied());
    }
}
