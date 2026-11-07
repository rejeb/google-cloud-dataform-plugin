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
package io.github.rejeb.dataform.language.gcp.toolwindow;

import com.intellij.icons.AllIcons;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.components.JBList;
import io.github.rejeb.dataform.language.gcp.settings.DataformRepositoryConfig;

public class RepositoryListCellRendererTest extends BasePlatformTestCase {

    public void testTheLabelComesFirstThenProjectAndLocation() {
        DataformRepositoryConfig config = new DataformRepositoryConfig("id", "Sales", "my-project", "sales", "europe-west1", "");
        RepositoryListCellRenderer renderer = new RepositoryListCellRenderer();

        renderer.getListCellRendererComponent(new JBList<>(config), config, 0, false, false);

        assertEquals("Sales  my-project · europe-west1", renderer.getCharSequence(false).toString());
        assertEquals("Sales", renderer.getCharSequence(true).toString());
        assertSame(AllIcons.Nodes.DataSchema, renderer.getIcon());
    }

    public void testAnIncompleteRepositoryShowsOnlyItsLabel() {
        DataformRepositoryConfig config = new DataformRepositoryConfig("id", "Repository 1", "", "", "");
        RepositoryListCellRenderer renderer = new RepositoryListCellRenderer();

        renderer.getListCellRendererComponent(new JBList<>(config), config, 0, false, false);

        assertEquals("Repository 1", renderer.getCharSequence(false).toString());
    }
}
