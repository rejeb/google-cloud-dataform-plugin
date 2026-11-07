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
package io.github.rejeb.dataform.language.fileEditor;

import io.github.rejeb.dataform.language.fileEditor.cost.CostEstimate;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The bytes the actions of a file would process together.
 *
 * @param bytes     sum of the estimated bytes
 * @param estimated number of actions with an estimate
 * @param actions   number of actions with a query to estimate
 */
record PreviewCostTotal(long bytes, int estimated, int actions) {

    @NotNull
    static PreviewCostTotal of(@NotNull List<CostEstimate> done, int actions) {
        long bytes = 0;
        int estimated = 0;
        for (CostEstimate estimate : done) {
            if (!estimate.failed() && estimate.bytesProcessed() != null) {
                bytes += estimate.bytesProcessed();
                estimated++;
            }
        }
        return new PreviewCostTotal(bytes, estimated, actions);
    }

    boolean visible() {
        return estimated >= 1;
    }
}
