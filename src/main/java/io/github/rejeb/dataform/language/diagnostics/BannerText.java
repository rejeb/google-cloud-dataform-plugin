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

import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds the text of the banners reporting Dataform problems. Messages coming from the compiler or
 * from BigQuery are multi-line, so they are collapsed to one logical line each and left unwrapped:
 * the wrapping belongs to {@link WrappingEditorNotificationPanel}, which knows the banner width.
 */
public final class BannerText {

    private static final String BULLET = "• ";

    private BannerText() {
    }

    /**
     * One message on the header line, several as a bulleted list below it.
     */
    @NotNull
    public static String of(@NotNull String header, @NotNull List<String> messages, @NotNull String fallback) {
        if (messages.size() == 1) {
            return header + " " + collapse(messages.getFirst(), fallback);
        }
        return header + "\n" + messages.stream()
                .map(message -> BULLET + collapse(message, fallback))
                .collect(Collectors.joining("\n"));
    }

    /**
     * Squeezes a message onto a single logical line, replacing an empty one with the fallback.
     */
    @NotNull
    public static String collapse(@NotNull String message, @NotNull String fallback) {
        String collapsed = message.replaceAll("\\s+", " ").trim();
        return collapsed.isEmpty() ? fallback : collapsed;
    }
}
