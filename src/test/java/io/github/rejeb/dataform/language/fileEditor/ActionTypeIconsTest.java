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
package io.github.rejeb.dataform.language.fileEditor;

import io.github.rejeb.dataform.language.DataformIcons;
import junit.framework.TestCase;

public class ActionTypeIconsTest extends TestCase {

    public void testTablesViewsAndIncrementalTablesShowTheBlueTable() {
        assertSame(DataformIcons.ACTION_TABLE, ActionTypeIcons.of("table"));
        assertSame(DataformIcons.ACTION_TABLE, ActionTypeIcons.of("view"));
        assertSame(DataformIcons.ACTION_TABLE, ActionTypeIcons.of("incremental"));
    }

    public void testAssertionsShowTheShield() {
        assertSame(DataformIcons.ACTION_ASSERTION, ActionTypeIcons.of("assertion"));
    }

    public void testOperationsShowTheDatabase() {
        assertSame(DataformIcons.ACTION_OPERATIONS, ActionTypeIcons.of("operation"));
        assertSame(DataformIcons.ACTION_OPERATIONS, ActionTypeIcons.of("operations"));
    }

    public void testDeclarationsShowTheGreyTable() {
        assertSame(DataformIcons.ACTION_DECLARATION, ActionTypeIcons.of("declaration"));
    }

    public void testAnUnknownTypeShowsTheTable() {
        assertSame(DataformIcons.ACTION_TABLE, ActionTypeIcons.of(null));
    }
}
