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

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataformEvalScriptBuilderTest {

    private static final DataformEvaluationContext CONTEXT = new DataformEvaluationContext(
            Map.of("defaultDatabase", "my-project", "defaultSchema", "my_dataset"),
            Map.of("users", "my-project.my_dataset.users"),
            "my-project.my_dataset.mart",
            Map.of("constants", "module.exports = { SUFFIX: '_v1' };"),
            "const LOCAL = 'from js block';",
            List.of("/usr/lib/node_modules"));

    @Test
    void payloadCarriesTheEvaluationEnvironment() {
        String payload = DataformEvalScriptBuilder.payload(CONTEXT, List.of("ref('users')"));

        assertTrue(payload.contains("\"defaultDatabase\":\"my-project\""), payload);
        assertTrue(payload.contains("\"users\":\"my-project.my_dataset.users\""), payload);
        assertTrue(payload.contains("\"selfTarget\":\"my-project.my_dataset.mart\""), payload);
        assertTrue(payload.contains("\"constants\":"), payload);
        assertTrue(payload.contains("from js block"), payload);
        assertTrue(payload.contains("/usr/lib/node_modules"), payload);
        assertTrue(payload.contains("\"expressions\":[\"ref(\\u0027users\\u0027)\"]"), payload);
    }

    @Test
    void scriptIsStableSoTheCachedFileCanBeReused() {
        assertEquals(DataformEvalScriptBuilder.script(), DataformEvalScriptBuilder.script());
        assertTrue(DataformEvalScriptBuilder.script().contains("vm.createContext"));
        assertTrue(DataformEvalScriptBuilder.script().contains("payload.refTargets"));
    }

    @Test
    void expressionsAreEvaluatedInTheScopeOfTheFileScript() {
        String script = DataformEvalScriptBuilder.script();

        assertTrue(script.contains("payload.fileScript"), script);
        assertTrue(script.contains("return (' + source + ');"), script);
        assertTrue(script.contains("evaluate(source, false)"), script);
    }

    @Test
    void objectValuesAreSerializedAsIndentedJson() {
        String script = DataformEvalScriptBuilder.script();

        assertTrue(script.contains("JSON.stringify(value, null, 2)"), script);
    }

    @Test
    void generatedStringLiteralsUseEscapedNewlines() {
        for (String line : DataformEvalScriptBuilder.script().split("\n")) {
            assertEquals(0, countUnescapedQuotes(line) % 2,
                    "a raw newline inside a JavaScript string literal makes the harness unparseable: " + line);
        }
    }

    private static int countUnescapedQuotes(String line) {
        int count = 0;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == '\'' && (i == 0 || line.charAt(i - 1) != '\\')) {
                count++;
            }
        }
        return count;
    }
}
