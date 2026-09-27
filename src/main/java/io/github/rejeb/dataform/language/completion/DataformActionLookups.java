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
package io.github.rejeb.dataform.language.completion;

import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.compilation.model.Target;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.util.List;

public final class DataformActionLookups {

    public static final double ACTION_PRIORITY = 200.0;

    private DataformActionLookups() {
    }

    /**
     * Returns a lookup per enabled table, view and incremental table of the graph.
     */
    @NotNull
    public static List<LookupElement> tables(@NotNull CompiledGraph graph) {
        return graph.getTables().stream()
                .filter(table -> table.getTarget() != null && !table.isDisabled())
                .map(table -> lookup(table.getTarget(), AllIcons.Nodes.DataTables, table.getType(), ACTION_PRIORITY))
                .toList();
    }

    /**
     * Returns a lookup per declared source of the graph.
     */
    @NotNull
    public static List<LookupElement> declarations(@NotNull CompiledGraph graph) {
        return graph.getDeclarations().stream()
                .filter(declaration -> declaration.getTarget() != null)
                .map(declaration -> lookup(declaration.getTarget(), AllIcons.Nodes.DataSchema, "source", ACTION_PRIORITY))
                .toList();
    }

    /**
     * Returns a lookup completing the name of the given target, showing its full name as tail text.
     */
    @NotNull
    public static LookupElement lookup(@NotNull Target target,
                                       @NotNull Icon icon,
                                       @Nullable String typeText,
                                       double priority) {
        LookupElementBuilder element = LookupElementBuilder.create(target.getName())
                .withIcon(icon)
                .withTypeText(typeText)
                .withTailText(" (" + target.getFullName() + ")", true);
        return PrioritizedLookupElement.withPriority(element, priority);
    }
}
