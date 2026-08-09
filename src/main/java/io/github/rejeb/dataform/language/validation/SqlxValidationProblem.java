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
package io.github.rejeb.dataform.language.validation;

import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

/**
 * A validation problem anchored to an exact range of the host document.
 */
public record SqlxValidationProblem(@NotNull TextRange range,
                                    @NotNull String message,
                                    @NotNull Kind kind) {

    /**
     * The category of problem, used only for grouping and tests.
     */
    public enum Kind {
        UNRESOLVED_REFERENCE,
        UNKNOWN_CONFIG_KEY,
        INVALID_CONFIG_VALUE
    }
}
