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
package io.github.rejeb.dataform.language.lineage.model;

import io.github.rejeb.dataform.language.columns.model.ColumnRef;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The column rows a node card shows, and how many more it leaves out.
 *
 * @param rows   the columns listed in the card, in table order, at most {@link #MAX_ROWS}
 * @param hidden the number of columns the card would list beyond that limit
 */
public record CardColumns(@NotNull List<ColumnRef> rows, int hidden) {

    /** A card never lists more rows than this. */
    public static final int MAX_ROWS = 12;

    /** A card without any row. */
    public static final CardColumns NONE = new CardColumns(List.of(), 0);

    public CardColumns {
        rows = List.copyOf(rows);
    }
}
