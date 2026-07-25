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
package io.github.rejeb.dataform.language.evaluation;

import com.intellij.openapi.util.TextRange;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataformTemplateSyntaxTest {

    @Test
    void graphFunctionsAreDeterministic() {
        assertTrue(DataformTemplateSyntax.isDeterministic("ref(\"bronze_orders\")"));
        assertTrue(DataformTemplateSyntax.isDeterministic("self()"));
        assertTrue(DataformTemplateSyntax.isDeterministic("resolve(\"x\")"));
        assertTrue(DataformTemplateSyntax.isDeterministic("name()"));
        assertTrue(DataformTemplateSyntax.isDeterministic("schema()"));
        assertTrue(DataformTemplateSyntax.isDeterministic("database()"));
        assertTrue(DataformTemplateSyntax.isDeterministic("schema_helpers.incrementalWhereClause(\"order_ts\", 3)"));
        assertTrue(DataformTemplateSyntax.isDeterministic("dataform.projectConfig.vars.silver_dataset"));
    }

    @Test
    void executionDependentAndDefinitionFunctionsAreNot() {
        assertFalse(DataformTemplateSyntax.isDeterministic("incremental()"));
        assertFalse(DataformTemplateSyntax.isDeterministic("when(incremental(), `WHERE x`)"));
        assertFalse(DataformTemplateSyntax.isDeterministic("ctx.when(ctx.incremental(), \"a\")"));
        assertFalse(DataformTemplateSyntax.isDeterministic("publish(\"x\")"));
        assertFalse(DataformTemplateSyntax.isDeterministic("operate(\"x\")"));
        assertFalse(DataformTemplateSyntax.isDeterministic("assert(\"x\")"));
        assertFalse(DataformTemplateSyntax.isDeterministic("declare({name: \"x\"})"));
    }

    @Test
    void aNameContainingAKeywordIsStillDeterministic() {
        assertTrue(DataformTemplateSyntax.isDeterministic("helpers.whenReady()"));
        assertTrue(DataformTemplateSyntax.isDeterministic("helpers.incrementalWhereClause(\"ts\", 3)"));
        assertTrue(DataformTemplateSyntax.isDeterministic("my_assert_helpers.build()"));
    }

    @Test
    void aDeterministicExpressionFoldsAsAWhole() {
        String text = "${ref(\"bronze_orders\")}";

        assertEquals(List.of(new TextRange(0, text.length())), DataformTemplateSyntax.foldableRanges(text));
    }

    @Test
    void onlyTheNestedSubstitutionsOfAWhenExpressionFold() {
        String text = "${when(incremental(), `WHERE ${schema_helpers.incrementalWhereClause(\"order_ts\", 3)}`)}";

        List<TextRange> ranges = DataformTemplateSyntax.foldableRanges(text);

        assertEquals(1, ranges.size());
        assertEquals("${schema_helpers.incrementalWhereClause(\"order_ts\", 3)}",
                ranges.getFirst().substring(text));
    }

    @Test
    void multilineWhenExpressionFoldsSelfAndTheHelperOnly() {
        String text = """
                ${when(incremental(), `
                    DELETE FROM ${self()}
                    WHERE ${schema_helpers.incrementalWhereClause("order_ts", 3)}
                  `)}""";

        List<TextRange> ranges = DataformTemplateSyntax.foldableRanges(text);

        assertEquals(2, ranges.size());
        assertEquals("${self()}", ranges.get(0).substring(text));
        assertEquals("${schema_helpers.incrementalWhereClause(\"order_ts\", 3)}", ranges.get(1).substring(text));
    }

    @Test
    void nestingIsFollowedToAnyDepth() {
        String text = "${when(incremental(), `${when(incremental(), `${self()}`)}`)}";

        List<TextRange> ranges = DataformTemplateSyntax.foldableRanges(text);

        assertEquals(1, ranges.size());
        assertEquals("${self()}", ranges.getFirst().substring(text));
    }

    @Test
    void aBlockedExpressionWithoutNestingFoldsNothing() {
        assertEquals(List.of(), DataformTemplateSyntax.foldableRanges("${incremental()}"));
    }
}
