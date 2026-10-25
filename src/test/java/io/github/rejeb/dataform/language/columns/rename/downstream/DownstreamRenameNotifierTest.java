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
package io.github.rejeb.dataform.language.columns.rename.downstream;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DownstreamRenameNotifierTest {

    private static final DownstreamRename EMAIL =
            new DownstreamRename("p.d.customers", "email", "customer_email", Set.of("p.d.customers"));

    @Test
    void theBalloonSaysHowManyActionsStillReadTheOldName() {
        assertEquals("Column `email` was renamed to `customer_email` in `customers`: 2 downstream actions still read `email`",
                DownstreamRenameNotifier.textOf(EMAIL, 2));
        assertEquals("Column `email` was renamed to `customer_email` in `customers`: 1 downstream action still reads `email`",
                DownstreamRenameNotifier.textOf(EMAIL, 1));
    }
}
