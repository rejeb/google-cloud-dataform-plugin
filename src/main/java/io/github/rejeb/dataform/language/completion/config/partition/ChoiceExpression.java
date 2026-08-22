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
package io.github.rejeb.dataform.language.completion.config.partition;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.template.Result;
import com.intellij.codeInsight.template.TextResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

/**
 * A template placeholder proposing a fixed list of values, the first of which it starts on.
 */
class ChoiceExpression extends Expression {

    private final List<String> choices;
    private final Function<String, LookupElement> presentation;

    ChoiceExpression(@NotNull List<String> choices,
                     @NotNull Function<String, LookupElement> presentation) {
        this.choices = choices;
        this.presentation = presentation;
    }

    ChoiceExpression(@NotNull List<String> choices) {
        this(choices, LookupElementBuilder::create);
    }

    @Override
    public @Nullable Result calculateResult(ExpressionContext context) {
        return choices.isEmpty() ? new TextResult("") : new TextResult(choices.getFirst());
    }

    @Override
    public @Nullable Result calculateQuickResult(ExpressionContext context) {
        return calculateResult(context);
    }

    /**
     * The values the stop offers. A lone value is offered too rather than silently filled in, so
     * that the popup shows what the placeholder was resolved to.
     */
    @Override
    public LookupElement @Nullable [] calculateLookupItems(ExpressionContext context) {
        return choices.isEmpty()
                ? null
                : choices.stream().map(presentation).toArray(LookupElement[]::new);
    }
}
