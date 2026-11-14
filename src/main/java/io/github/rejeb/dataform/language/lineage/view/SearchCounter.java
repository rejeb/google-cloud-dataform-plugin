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
package io.github.rejeb.dataform.language.lineage.view;

import io.github.rejeb.dataform.language.lineage.model.LineageModel;
import io.github.rejeb.dataform.language.lineage.model.SearchScope;
import org.jetbrains.annotations.NotNull;

/**
 * The text of the counter next to the search field of the lineage view.
 */
final class SearchCounter {

    private SearchCounter() {
    }

    /**
     * @return the number of tables or of columns the search found, with a hint when it found too
     * many columns for their lineage to be shown and the number of tables whose columns could
     * not be read; empty while the search text is blank
     */
    static @NotNull String text(@NotNull LineageModel model) {
        if (model.searchQuery().isBlank()) return "";
        if (model.searchScope() == SearchScope.TABLES) return model.searchMatches().size() + " found";
        int found = model.foundColumnIds().size();
        String count = found + (found == 1 ? " column" : " columns");
        boolean lineageLeftOut = model.tooManyFoundColumns() && model.selectedColumnIds().isEmpty();
        String text = lineageLeftOut ? count + " · type more to show lineage" : count;
        int unsearched = model.unsearchedTableCount();
        if (unsearched == 0) return text;
        return text + " · " + unsearched + (unsearched == 1 ? " table" : " tables") + " not searched";
    }
}
