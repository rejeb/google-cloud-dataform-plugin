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
package io.github.rejeb.dataform.language.schema.sql;

import io.github.rejeb.dataform.language.util.MappedText;
import io.github.rejeb.dataform.language.util.Utils;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class DryRunQueryTextTest {

    private static final MappedText QUERY =
            MappedText.identity(DryRunQueryText.MAIN_QUERY, "SELECT a FROM t");

    @Test
    public void theTextIsTheOneTheTextOnlyHelperBuilds() {
        List<List<String>> cases = List.of(
                List.of(),
                List.of("DECLARE x INT64 DEFAULT 1"),
                List.of("  SET x = 2;  ", " "),
                Arrays.asList("DECLARE y STRING", null),
                List.of(" "));
        for (List<String> preOperations : cases) {
            assertEquals(Utils.withPreOperations(preOperations, QUERY.text()),
                    DryRunQueryText.withPreOperations(preOperations, QUERY).text());
        }
        assertEquals(QUERY.text(), DryRunQueryText.withPreOperations(null, QUERY).text());
    }

    @Test
    public void thePreOperationsTraceToTheirOwnSource() {
        MappedText sent = DryRunQueryText.withPreOperations(List.of("DECLARE x INT64 DEFAULT 1"), QUERY);

        assertEquals(new MappedText.SourcePosition(DryRunQueryText.PRE_OPERATIONS, 8, true), sent.toSource(8));
        assertEquals("DECLARE x INT64 DEFAULT 1;", sent.sourceText(DryRunQueryText.PRE_OPERATIONS));
    }

    @Test
    public void theQueryAfterThePreOperationsTracesToTheMainQuery() {
        MappedText sent = DryRunQueryText.withPreOperations(List.of("DECLARE x INT64 DEFAULT 1"), QUERY);

        assertEquals(new MappedText.SourcePosition(DryRunQueryText.MAIN_QUERY, 7, true),
                sent.toSource(sent.text().indexOf("a FROM")));
        assertNull(sent.toSource(sent.text().length() - 1));
    }
}
