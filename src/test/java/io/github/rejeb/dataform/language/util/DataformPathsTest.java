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
package io.github.rejeb.dataform.language.util;

import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class DataformPathsTest extends BasePlatformTestCase {

    public void testNormalizeTurnsWindowsSeparatorsIntoSlashes() {
        assertEquals("definitions/staging/orders.sqlx",
                DataformPaths.normalize("definitions\\staging\\orders.sqlx"));
        assertEquals("C:/work/project/definitions/orders.sqlx",
                DataformPaths.normalize("C:\\work\\project\\definitions\\orders.sqlx"));
        assertEquals("definitions/orders.sqlx", DataformPaths.normalize("definitions/orders.sqlx"));
        assertNull(DataformPaths.normalize(null));
    }

    public void testPointsToMatchesWhateverSeparatorEitherSideUses() {
        assertTrue(DataformPaths.pointsTo("/p/definitions/orders.sqlx", "definitions\\orders.sqlx"));
        assertTrue(DataformPaths.pointsTo("C:\\p\\definitions\\orders.sqlx", "definitions/orders.sqlx"));
        assertTrue(DataformPaths.pointsTo("definitions\\orders.sqlx", "definitions/orders.sqlx"));
    }

    public void testPointsToOnlyMatchesOnASegmentBoundary() {
        assertFalse(DataformPaths.pointsTo("/p/definitions/old_orders.sqlx", "orders.sqlx"));
        assertFalse(DataformPaths.pointsTo("/p/sub_definitions/orders.sqlx", "definitions/orders.sqlx"));
        assertTrue(DataformPaths.pointsTo("/p/definitions/orders.sqlx", "orders.sqlx"));
    }

    public void testPointsToIgnoresALeadingSlashOnTheRelativePath() {
        assertTrue(DataformPaths.pointsTo("/p/definitions/orders.sqlx", "/definitions/orders.sqlx"));
    }

    public void testPointsToRejectsMissingOrBlankPaths() {
        assertFalse(DataformPaths.pointsTo(null, "definitions/orders.sqlx"));
        assertFalse(DataformPaths.pointsTo("/p/definitions/orders.sqlx", null));
        assertFalse(DataformPaths.pointsTo("/p/definitions/orders.sqlx", " "));
        assertFalse(DataformPaths.pointsTo("/p/definitions/orders.sqlx", "/"));
    }

    public void testFindInProjectResolvesAWindowsRelativePath() {
        PsiFile file = myFixture.addFileToProject("definitions/staging/orders.sqlx", "SELECT 1");

        assertEquals(file.getVirtualFile(),
                DataformPaths.findInProject(getProject(), "definitions\\staging\\orders.sqlx"));
        assertEquals(file.getVirtualFile(),
                DataformPaths.findInProject(getProject(), "definitions/staging/orders.sqlx"));
    }

    public void testFindInProjectReturnsNullForMissingOrBlankPaths() {
        assertNull(DataformPaths.findInProject(getProject(), "definitions\\missing.sqlx"));
        assertNull(DataformPaths.findInProject(getProject(), null));
        assertNull(DataformPaths.findInProject(getProject(), ""));
    }
}
