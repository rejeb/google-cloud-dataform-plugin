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

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import io.github.rejeb.dataform.language.service.WorkflowSettingsProperty;
import io.github.rejeb.dataform.language.service.WorkflowSettingsService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the workflow settings lookup elements as complete property paths, so a
 * single choice inserts the whole path instead of chaining popups.
 */
final class WorkflowSettingsLookupFactory {

    private static final String TYPE_TEXT = "workflow_settings.yaml";

    private WorkflowSettingsLookupFactory() {
    }

    /**
     * Returns one element per leaf property below the given prefix, each inserting
     * its full path relative to that prefix.
     */
    static List<LookupElement> leafElements(@NotNull WorkflowSettingsService service,
                                            @Nullable String prefix) {
        List<LookupElement> elements = new ArrayList<>();
        for (String path : service.getLeafPathsForPrefix(prefix)) {
            elements.add(leafElement(service, prefix, path));
        }
        return elements;
    }

    private static LookupElement leafElement(@NotNull WorkflowSettingsService service,
                                             @Nullable String prefix,
                                             @NotNull String path) {
        String leafName = path.substring(path.lastIndexOf('.') + 1);
        LookupElementBuilder element = LookupElementBuilder.create(path)
                .withLookupString(leafName)
                .withTypeText(TYPE_TEXT)
                .withIcon(AllIcons.Nodes.Variable);

        WorkflowSettingsProperty property = service.getProperty(
                prefix == null || prefix.isEmpty() ? path : prefix + "." + path);
        if (property != null && property.value() != null) {
            element = element.withTailText(" = " + property.value(), true);
        }
        return element;
    }
}
