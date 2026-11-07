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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Reads service account emails out of the IAM service account list and the project IAM policy.
 */
final class ServiceAccountResponseParser {

    private static final String SERVICE_ACCOUNT_MEMBER = "serviceAccount:";

    private ServiceAccountResponseParser() {
    }

    /**
     * One page of {@code projects.serviceAccounts.list}.
     *
     * @param emails        the emails of the accounts on the page
     * @param nextPageToken the token of the next page, {@code null} on the last page
     */
    record AccountsPage(@NotNull List<String> emails, @Nullable String nextPageToken) {
    }

    /**
     * @param json a {@code projects.serviceAccounts.list} response body
     * @return the emails of the accounts the page holds and its next page token
     */
    @NotNull
    static AccountsPage accountsPage(@NotNull String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        List<String> emails = new ArrayList<>();
        for (JsonElement element : array(root, "accounts")) {
            if (element.isJsonObject()) {
                String email = string(element.getAsJsonObject(), "email");
                if (email != null && !email.isBlank()) {
                    emails.add(email);
                }
            }
        }
        return new AccountsPage(emails, string(root, "nextPageToken"));
    }

    /**
     * Keeps the service accounts granted a role in the policy, Google-provided service agents
     * included, leaving out deleted accounts and Kubernetes workload identities.
     *
     * @param json a {@code projects.getIamPolicy} response body
     * @return the distinct service account emails, sorted
     */
    @NotNull
    static List<String> policyServiceAccounts(@NotNull String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        TreeSet<String> emails = new TreeSet<>();
        for (JsonElement binding : array(root, "bindings")) {
            if (!binding.isJsonObject()) {
                continue;
            }
            for (JsonElement member : array(binding.getAsJsonObject(), "members")) {
                if (!member.isJsonPrimitive()) {
                    continue;
                }
                String value = member.getAsString();
                if (value.startsWith(SERVICE_ACCOUNT_MEMBER) && value.indexOf('@') > 0) {
                    emails.add(value.substring(SERVICE_ACCOUNT_MEMBER.length()));
                }
            }
        }
        return List.copyOf(emails);
    }

    @NotNull
    private static JsonArray array(@NotNull JsonObject object, @NotNull String name) {
        JsonElement value = object.get(name);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
    }

    @Nullable
    private static String string(@NotNull JsonObject object, @NotNull String name) {
        JsonElement value = object.get(name);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }
}
