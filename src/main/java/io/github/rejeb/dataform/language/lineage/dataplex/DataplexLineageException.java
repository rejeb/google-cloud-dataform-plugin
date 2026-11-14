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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A failed read of the recorded lineage, with what the user can do about it.
 */
public final class DataplexLineageException extends RuntimeException {

    /** Why the read failed. */
    public enum Kind {
        /** The Data Lineage API is not enabled in the project. */
        API_DISABLED,
        /** The account is not allowed to read the lineage. */
        PERMISSION_DENIED,
        /** No usable Google credential: the user must sign in. */
        NOT_SIGNED_IN,
        /** Anything else, such as a network failure. */
        OTHER
    }

    private final Kind kind;

    public DataplexLineageException(@NotNull Kind kind, @NotNull String message, @Nullable Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    /**
     * @return why the read failed
     */
    public @NotNull Kind kind() {
        return kind;
    }
}
