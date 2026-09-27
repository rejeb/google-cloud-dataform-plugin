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
package io.github.rejeb.dataform.language.refactoring.column;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DataformColumnNameValidatorTest {

    @Test
    public void plainIdentifiersAreValid() {
        assertTrue(DataformColumnNameValidator.isValid("customer_id"));
        assertTrue(DataformColumnNameValidator.isValid("_x1"));
        assertTrue(DataformColumnNameValidator.isValid("A"));
    }

    @Test
    public void rejectsEmptyLeadingDigitAndSpecialCharacters() {
        assertFalse(DataformColumnNameValidator.isValid(""));
        assertFalse(DataformColumnNameValidator.isValid("1abc"));
        assertFalse(DataformColumnNameValidator.isValid("a-b"));
        assertFalse(DataformColumnNameValidator.isValid("a b"));
        assertFalse(DataformColumnNameValidator.isValid("é"));
    }

    @Test
    public void rejectsNamesLongerThanBigQueryAllows() {
        assertTrue(DataformColumnNameValidator.isValid("a".repeat(300)));
        assertFalse(DataformColumnNameValidator.isValid("a".repeat(301)));
    }

    @Test
    public void quotesNonPlainNamesInSql() {
        assertEquals("customer_id", DataformColumnNameValidator.inSql("customer_id"));
        assertEquals("`a-b`", DataformColumnNameValidator.inSql("a-b"));
    }

    @Test
    public void quotesNonPlainNamesAsConfigKeys() {
        assertEquals("customer_id", DataformColumnNameValidator.asConfigKey("customer_id"));
        assertEquals("\"a-b\"", DataformColumnNameValidator.asConfigKey("a-b"));
    }
}
