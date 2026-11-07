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
package io.github.rejeb.dataform.language.ui;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Small text conventions shared by the plugin views.
 */
public final class UiText {

    /**
     * The separator {@link #dot(String...)} puts between parts.
     */
    public static final String SEPARATOR = " · ";

    private UiText() {
    }

    /**
     * A title followed by its count in parentheses, such as {@code Jobs (3)}; the bare title when
     * the count is zero.
     */
    public static @NotNull String counted(@NotNull String title, int count) {
        return count == 0 ? title : title + " (" + count + ")";
    }

    /**
     * Joins the non-blank parts with a middle dot, such as {@code table · 4 s}.
     */
    public static @NotNull String dot(@Nullable String... parts) {
        return Arrays.stream(parts)
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(part -> !part.isEmpty())
                .collect(Collectors.joining(SEPARATOR));
    }

    /**
     * A tooltip showing the given plain text as it is, line breaks included.
     */
    public static @NotNull String htmlTooltip(@NotNull String text) {
        return XmlStringUtil.wrapInHtml(StringUtil.escapeXmlEntities(text.strip()).replace("\n", "<br>"));
    }
}
