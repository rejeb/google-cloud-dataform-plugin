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
package io.github.rejeb.dataform.language.diagnostics;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Classifies an error message: the message gets the kind of the first rule whose pattern matches
 * all of it.
 *
 * @param kind    the kind given to a matching message
 * @param pattern the pattern a whole message must match
 * @param <K>     the kind of error message
 */
public record MessageRule<K>(@NotNull K kind, @NotNull Pattern pattern) {

    /**
     * A message matched by a rule.
     *
     * @param kind    the kind of the rule
     * @param matcher the matcher, to read the named groups of the rule from
     * @param <K>     the kind of error message
     */
    public record Match<K>(@NotNull K kind, @NotNull Matcher matcher) {
    }

    /**
     * Creates a rule from a regular expression.
     */
    public static <K> @NotNull MessageRule<K> of(@NotNull K kind, @NotNull String regex) {
        return new MessageRule<>(kind, Pattern.compile(regex));
    }

    /**
     * @return the match of the first rule that matches the whole message, or {@code null} when none does
     */
    public static <K> @Nullable Match<K> firstMatch(@NotNull List<MessageRule<K>> rules, @NotNull String message) {
        for (MessageRule<K> rule : rules) {
            Matcher matcher = rule.pattern().matcher(message);
            if (matcher.matches()) {
                return new Match<>(rule.kind(), matcher);
            }
        }
        return null;
    }
}
