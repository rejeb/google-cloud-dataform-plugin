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

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.sql.psi.SqlCompositeElementTypes;
import io.github.rejeb.dataform.language.refactoring.column.usage.InjectedSqlFiles;
import io.github.rejeb.dataform.language.refactoring.column.usage.SqlxStarExpander;
import io.github.rejeb.dataform.language.schema.sql.SqlPsiParts;

import java.util.List;

/**
 * What a star expands to is the column list of the action, which is the whole output of its query.
 *
 * <p>So a star can only be expanded where it is what the query selects. Beside another item the
 * expansion would write that item's column a second time, and the action would stop compiling on a
 * duplicate name.</p>
 */
public class SqlxStarExpansionTest extends ColumnRenameFixture {

    private SqlxStarExpander.Expansion expansionOf(String query) {
        installProject(new Action("src", "SELECT 'a' AS full_name, 1 AS score", List.of(),
                List.of("full_name", "score")));
        PsiFile file = addFile("src", "config { type: \"table\" }\n\n" + query + "\n");
        PsiElement star = starOf(file);
        assertNotNull("the query has a star to expand\n" + file.getText(), star);
        return SqlxStarExpander.of(file, star, "full_name", "display_name");
    }

    /** The star of the select list, which is the element the plan holds for a starred column. */
    private PsiElement starOf(PsiFile hostFile) {
        for (PsiFile injected : InjectedSqlFiles.all(hostFile)) {
            for (PsiElement element : PsiTreeUtil.collectElements(injected, candidate ->
                    SqlPsiParts.isType(candidate, SqlCompositeElementTypes.SQL_COLUMN_REFERENCE)
                            && SqlPsiParts.isStar(candidate))) {
                return element;
            }
        }
        return null;
    }

    public void testAStarThatIsAllTheQuerySelectsBecomesTheColumnList() {
        SqlxStarExpander.Expansion expansion = expansionOf("SELECT *\nFROM `p.d.other`");

        assertTrue("nothing stands in the way of this one: " + expansion.blockers(),
                expansion.isPossible());
        assertEquals("full_name AS display_name, score", expansion.text());
    }

    public void testTheExceptListIsAppliedAndReplacedAlongWithTheStar() {
        SqlxStarExpander.Expansion expansion =
                expansionOf("SELECT * EXCEPT (score)\nFROM `p.d.other`");

        assertTrue("an EXCEPT list is part of the star, not another item: " + expansion.blockers(),
                expansion.isPossible());
        assertEquals("full_name AS display_name", expansion.text());
        assertEquals("the EXCEPT list is written over too", "* EXCEPT (score)".length(),
                expansion.replacedLength());
    }

    public void testAStarBesideAnotherItemIsNotExpanded() {
        SqlxStarExpander.Expansion expansion =
                expansionOf("SELECT *, 2 AS doubled\nFROM `p.d.other`");

        assertFalse("expanding here would write the column list beside the item it already holds",
                expansion.isPossible());
        assertEquals(List.of("the select list holds items beside the star"), expansion.blockers());
    }

    public void testASecondStarStopsTheExpansionToo() {
        SqlxStarExpander.Expansion expansion =
                expansionOf("SELECT a.*, b.*\nFROM `p.d.other` a JOIN `p.d.more` b USING (full_name)");

        assertFalse("each star would be written the whole output of the action",
                expansion.isPossible());
    }
}
