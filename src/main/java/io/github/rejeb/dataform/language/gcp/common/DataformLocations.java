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

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The Google Cloud regions offered when choosing where a Dataform repository lives. Dataform
 * repositories are regional, so the BigQuery multi-regions are not offered; a region missing from
 * the list can still be typed.
 */
public final class DataformLocations {

    private static final List<String> REGIONS = List.of(
            "africa-south1",
            "asia-east1", "asia-east2",
            "asia-northeast1", "asia-northeast2", "asia-northeast3",
            "asia-south1", "asia-south2",
            "asia-southeast1", "asia-southeast2",
            "australia-southeast1", "australia-southeast2",
            "europe-central2", "europe-north1", "europe-southwest1",
            "europe-west1", "europe-west2", "europe-west3", "europe-west4", "europe-west6",
            "europe-west8", "europe-west9", "europe-west10", "europe-west12",
            "me-central1", "me-central2", "me-west1",
            "northamerica-northeast1", "northamerica-northeast2",
            "southamerica-east1", "southamerica-west1",
            "us-central1", "us-east1", "us-east4", "us-east5", "us-south1",
            "us-west1", "us-west2", "us-west3", "us-west4");

    private DataformLocations() {
    }

    /**
     * The regions, grouped by area in alphabetical order.
     */
    public static @NotNull List<String> all() {
        return REGIONS;
    }
}
