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
package io.github.rejeb.dataform.language.refactoring.column;

import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

/**
 * What BigQuery accepts as a column name, and how a name has to be written where it is used.
 */
public final class DataformColumnNameValidator {

    private static final Pattern PLAIN_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final int MAX_LENGTH = 300;

    private DataformColumnNameValidator() {
    }

    /** Whether a name can be a BigQuery column name at all. */
    public static boolean isValid(@NotNull String name) {
        return !name.isEmpty() && name.length() <= MAX_LENGTH && PLAIN_IDENTIFIER.matcher(name).matches();
    }

    /** Whether a name can be written without quoting. */
    public static boolean isPlainIdentifier(@NotNull String name) {
        return PLAIN_IDENTIFIER.matcher(name).matches();
    }

    /** The name as it has to be written in SQL, backtick-quoted when it is not a plain identifier. */
    public static @NotNull String inSql(@NotNull String name) {
        return isPlainIdentifier(name) ? name : "`" + name + "`";
    }

    /** The name as it has to be written as a key of a config object literal. */
    public static @NotNull String asConfigKey(@NotNull String name) {
        return isPlainIdentifier(name) ? name : "\"" + name + "\"";
    }
}
