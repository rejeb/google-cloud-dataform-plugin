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

import org.jetbrains.annotations.Nullable;

import java.time.Instant;

/**
 * When a table was last written, as far as the recorded lineage tells.
 *
 * @param lastWrite the time of the last recorded write, {@code null} when none was recorded in the range
 * @param writtenBy the name of the process that made that write, when it is known
 * @param view      whether the entity is a view, whose "write" is its creation and not the age of its data
 */
public record Freshness(@Nullable Instant lastWrite, @Nullable String writtenBy, boolean view) {
}
