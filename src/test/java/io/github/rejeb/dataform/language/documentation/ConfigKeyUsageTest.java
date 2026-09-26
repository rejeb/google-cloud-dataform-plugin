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
package io.github.rejeb.dataform.language.documentation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConfigKeyUsageTest {

    @Test
    public void columnNamingKeysHaveAUsage() {
        for (String key : new String[]{"columns", "partitionBy", "clusterBy", "uniqueKey",
                "uniqueKeys", "nonNull", "field"}) {
            assertNotNull(ConfigKeyUsage.of(key, false), key);
        }
    }

    @Test
    public void keysDescribedByTheSchemaHaveNone() {
        assertNull(ConfigKeyUsage.of("type", false));
        assertNull(ConfigKeyUsage.of("description", true));
    }

    @Test
    public void uniqueKeyDiffersUnderAssertions() {
        String table = ConfigKeyUsage.of("uniqueKey", false);
        String assertion = ConfigKeyUsage.of("uniqueKey", true);
        assertNotEquals(table, assertion);
        assertTrue(table.contains("incremental merge"));
        assertTrue(assertion.contains("must be unique"));
    }

    @Test
    public void columnEntryShowsTheColumnsUsage() {
        assertEquals(ConfigKeyUsage.of("columns", false), ConfigKeyUsage.columnEntry());
    }
}
