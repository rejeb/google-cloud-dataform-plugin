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
package io.github.rejeb.dataform.language.config.completion;

import com.intellij.lang.javascript.psi.JSArrayLiteralExpression;
import com.intellij.lang.javascript.psi.JSObjectLiteralExpression;
import com.intellij.lang.javascript.psi.JSProperty;
import com.intellij.openapi.util.Key;
import com.intellij.patterns.PatternCondition;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import io.github.rejeb.dataform.language.config.ConfigColumnSlots;
import io.github.rejeb.dataform.language.config.ConfigSchemaLookup;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * The places of a SQLX config block each config completion provider applies to.
 */
final class ConfigCompletionPatterns {

    static final Key<ConfigColumnSlots.Slot> COLUMN_SLOT = Key.create("dataform.config.columnSlot");

    private static final String TAGS_PROPERTY = "tags";

    private ConfigCompletionPatterns() {
    }

    static PsiElementPattern.Capture<PsiElement> inConfigBlock() {
        return PlatformPatterns.psiElement().with(new PatternCondition<>("inConfigBlock") {
            @Override
            public boolean accepts(@NotNull PsiElement position, ProcessingContext context) {
                return ConfigSchemaLookup.isInConfigBlock(position);
            }
        });
    }

    static PsiElementPattern.Capture<PsiElement> tagsArrayValue() {
        return inConfigBlock().with(new PatternCondition<>("tagsArrayValue") {
            @Override
            public boolean accepts(@NotNull PsiElement position, ProcessingContext context) {
                JSProperty property = PsiTreeUtil.getParentOfType(position, JSProperty.class, false);
                return property != null && TAGS_PROPERTY.equals(property.getName())
                        && ConfigSchemaLookup.isValuePosition(property, position)
                        && PsiTreeUtil.getParentOfType(position, JSArrayLiteralExpression.class, false) != null;
            }
        });
    }

    static PsiElementPattern.Capture<PsiElement> columnSlot() {
        return PlatformPatterns.psiElement().with(new PatternCondition<>("columnSlot") {
            @Override
            public boolean accepts(@NotNull PsiElement position, ProcessingContext context) {
                Optional<ConfigColumnSlots.Slot> slot = ConfigColumnSlots.at(position);
                slot.ifPresent(found -> context.put(COLUMN_SLOT, found));
                return slot.isPresent();
            }
        });
    }

    static PsiElementPattern.Capture<PsiElement> keyPosition() {
        return inConfigBlock().with(new PatternCondition<>("keyPosition") {
            @Override
            public boolean accepts(@NotNull PsiElement position, ProcessingContext context) {
                if (PsiTreeUtil.getParentOfType(position, JSObjectLiteralExpression.class, false) == null) {
                    return false;
                }
                JSProperty property = PsiTreeUtil.getParentOfType(position, JSProperty.class, false);
                return property == null || !ConfigSchemaLookup.isValuePosition(property, position);
            }
        });
    }
}
