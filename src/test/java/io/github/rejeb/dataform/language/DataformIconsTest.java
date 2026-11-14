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
package io.github.rejeb.dataform.language;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ToolWindowEP;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.icons.CachedImageIcon;
import com.intellij.util.ui.ImageUtil;

import javax.swing.Icon;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Map;

public class DataformIconsTest extends BasePlatformTestCase {

    public void testEveryPluginIconLoadsAt16Pixels() {
        Map<String, Icon> icons = Map.ofEntries(
                Map.entry("FILE", DataformIcons.FILE),
                Map.entry("FILE_DISABLED", DataformIcons.FILE_DISABLED),
                Map.entry("TEST_FILE", DataformIcons.TEST_FILE),
                Map.entry("TOOL_WINDOW", DataformIcons.TOOL_WINDOW),
                Map.entry("PREVIEW_QUERY", DataformIcons.PREVIEW_QUERY),
                Map.entry("PREVIEW_SCHEMA", DataformIcons.PREVIEW_SCHEMA),
                Map.entry("LINEAGE", DataformIcons.LINEAGE),
                Map.entry("PREVIEW_TEST", DataformIcons.PREVIEW_TEST),
                Map.entry("QUERIES", DataformIcons.QUERIES),
                Map.entry("FAILED_ONLY", DataformIcons.FAILED_ONLY),
                Map.entry("ACTION_ASSERTION", DataformIcons.ACTION_ASSERTION),
                Map.entry("ACTION_OPERATIONS", DataformIcons.ACTION_OPERATIONS),
                Map.entry("REPOSITORY", DataformIcons.REPOSITORY),
                Map.entry("MANAGE_REPOSITORIES", DataformIcons.MANAGE_REPOSITORIES),
                Map.entry("ACTION_DECLARATION", DataformIcons.ACTION_DECLARATION),
                Map.entry("SCHEMA_RECORD", DataformIcons.SCHEMA_RECORD));
        icons.forEach((name, icon) -> {
            assertEquals(name, 16, icon.getIconWidth());
            assertEquals(name, 16, icon.getIconHeight());
        });
    }

    public void testFileIconsAreTheConcept16Svgs() {
        assertTrue(pathOf(DataformIcons.FILE).endsWith("/icons/sqlxFile.svg"));
        assertTrue(pathOf(DataformIcons.FILE_DISABLED).endsWith("/icons/sqlxFileDisabled.svg"));
        assertTrue(pathOf(DataformIcons.TEST_FILE).endsWith("/icons/sqlxTestFile.svg"));
    }

    public void testToolWindowIconIsThePrototypeSvg() {
        assertTrue(pathOf(DataformIcons.TOOL_WINDOW).endsWith("/icons/toolWindowDataform.svg"));
    }

    public void testDataformToolsShowsTheToolWindowIcon() {
        ToolWindowEP dataformTools = ToolWindowEP.EP_NAME.getExtensionList().stream()
                .filter(toolWindow -> "Dataform Tools".equals(toolWindow.id))
                .findFirst()
                .orElseThrow();
        assertEquals("/icons/toolWindowDataform.svg", dataformTools.icon);
    }

    public void testToolWindowIconHasItsScreenInLightThemes() {
        assertTrue(hasScreen(IconLoader.getDarkIcon(DataformIcons.TOOL_WINDOW, false)));
        assertTrue(hasScreen(IconLoader.getDarkIcon(newUiToolWindowIcon(), false)));
    }

    public void testToolWindowIconIsFramelessInDarkThemes() {
        assertFalse(hasScreen(IconLoader.getDarkIcon(DataformIcons.TOOL_WINDOW, true)));
        assertFalse(hasScreen(IconLoader.getDarkIcon(newUiToolWindowIcon(), true)));
    }

    public void testEditorToolbarIconsAreThePrototypeSvgs() {
        assertTrue(pathOf(DataformIcons.PREVIEW_QUERY).endsWith("/icons/previewQuery.svg"));
        assertTrue(pathOf(DataformIcons.PREVIEW_SCHEMA).endsWith("/icons/previewSchema.svg"));
        assertTrue(pathOf(DataformIcons.PREVIEW_TEST).endsWith("/icons/previewTest.svg"));
    }

    public void testLineageIconIsTheSiteColouredCardsSvg() {
        assertTrue(pathOf(DataformIcons.LINEAGE).endsWith("/icons/lineage.svg"));
    }

    public void testServicesQueriesIconIsThePrototypeSvg() {
        assertTrue(pathOf(DataformIcons.QUERIES).endsWith("/icons/queries.svg"));
    }

    public void testRunConsoleIconsAreThePrototypeSvgs() {
        assertTrue(pathOf(DataformIcons.FAILED_ONLY).endsWith("/icons/failedOnly.svg"));
    }

    public void testQueryPreviewIconsAreThePrototypeSvgs() {
        assertTrue(pathOf(DataformIcons.ACTION_ASSERTION).endsWith("/icons/actionAssertion.svg"));
        assertTrue(pathOf(DataformIcons.ACTION_OPERATIONS).endsWith("/icons/actionOperations.svg"));
        assertTrue(pathOf(DataformIcons.ACTION_DECLARATION).endsWith("/icons/actionDeclaration.svg"));
        assertTrue(pathOf(DataformIcons.SCHEMA_RECORD).endsWith("/icons/schemaRecord.svg"));
    }

    public void testDataformToolsIconsAreThePrototypeSvgs() {
        assertTrue(pathOf(DataformIcons.REPOSITORY).endsWith("/icons/repository.svg"));
        assertTrue(pathOf(DataformIcons.MANAGE_REPOSITORIES).endsWith("/icons/manageRepositories.svg"));
    }

    public void testIconsWithAPlatformEquivalentAreTheBundledIcons() {
        Map<String, Icon[]> icons = Map.ofEntries(
                Map.entry("LAYOUT_EDITOR_ONLY", pair(DataformIcons.LAYOUT_EDITOR_ONLY, AllIcons.General.LayoutEditorOnly)),
                Map.entry("LAYOUT_EDITOR_AND_PREVIEW", pair(DataformIcons.LAYOUT_EDITOR_AND_PREVIEW, AllIcons.General.LayoutEditorPreview)),
                Map.entry("LAYOUT_PREVIEW_ONLY", pair(DataformIcons.LAYOUT_PREVIEW_ONLY, AllIcons.General.LayoutPreviewOnly)),
                Map.entry("COMPILE", pair(DataformIcons.COMPILE, AllIcons.Actions.Compile)),
                Map.entry("RUN", pair(DataformIcons.RUN, AllIcons.Actions.Execute)),
                Map.entry("STATUS_SUCCEEDED", pair(DataformIcons.STATUS_SUCCEEDED, AllIcons.RunConfigurations.TestPassed)),
                Map.entry("STATUS_FAILED", pair(DataformIcons.STATUS_FAILED, AllIcons.RunConfigurations.TestError)),
                Map.entry("STATUS_CANCELLED", pair(DataformIcons.STATUS_CANCELLED, AllIcons.RunConfigurations.TestTerminated)),
                Map.entry("STATUS_SKIPPED", pair(DataformIcons.STATUS_SKIPPED, AllIcons.RunConfigurations.TestSkipped)),
                Map.entry("STATUS_PENDING", pair(DataformIcons.STATUS_PENDING, AllIcons.RunConfigurations.TestNotRan)),
                Map.entry("STOP", pair(DataformIcons.STOP, AllIcons.Actions.Suspend)),
                Map.entry("TRASH", pair(DataformIcons.TRASH, AllIcons.Actions.GC)),
                Map.entry("DOWNLOAD", pair(DataformIcons.DOWNLOAD, AllIcons.Actions.Download)),
                Map.entry("PAGE_FIRST", pair(DataformIcons.PAGE_FIRST, AllIcons.Actions.Play_first)),
                Map.entry("PAGE_PREVIOUS", pair(DataformIcons.PAGE_PREVIOUS, AllIcons.Actions.Play_back)),
                Map.entry("PAGE_NEXT", pair(DataformIcons.PAGE_NEXT, AllIcons.Actions.Play_forward)),
                Map.entry("PAGE_LAST", pair(DataformIcons.PAGE_LAST, AllIcons.Actions.Play_last)),
                Map.entry("ACTION_TABLE", pair(DataformIcons.ACTION_TABLE, AllIcons.Nodes.DataTables)),
                Map.entry("SCHEMA_COLUMN", pair(DataformIcons.SCHEMA_COLUMN, AllIcons.Nodes.DataColumn)),
                Map.entry("REFRESH", pair(DataformIcons.REFRESH, AllIcons.Actions.Refresh)),
                Map.entry("COPY", pair(DataformIcons.COPY, AllIcons.Actions.Copy)),
                Map.entry("EXTERNAL_LINK", pair(DataformIcons.EXTERNAL_LINK, AllIcons.Ide.External_link_arrow)),
                Map.entry("BRANCH", pair(DataformIcons.BRANCH, AllIcons.Vcs.Branch)),
                Map.entry("PULL", pair(DataformIcons.PULL, AllIcons.Actions.CheckOut)),
                Map.entry("PUSH", pair(DataformIcons.PUSH, AllIcons.Vcs.Push)),
                Map.entry("CHEVRON_DOWN", pair(DataformIcons.CHEVRON_DOWN, AllIcons.General.ChevronDown)),
                Map.entry("ADD", pair(DataformIcons.ADD, AllIcons.General.Add)),
                Map.entry("DIFF", pair(DataformIcons.DIFF, AllIcons.Actions.Diff)),
                Map.entry("FETCH", pair(DataformIcons.FETCH, AllIcons.Vcs.Fetch)),
                Map.entry("ACCOUNT", pair(DataformIcons.ACCOUNT, AllIcons.General.User)));
        icons.forEach((name, icon) -> assertSame(name, icon[1], icon[0]));
    }

    private static Icon newUiToolWindowIcon() {
        Icon icon = IconLoader.getIcon("/icons/toolWindowDataform@20x20.svg", DataformIcons.class);
        assertEquals(20, icon.getIconWidth());
        return icon;
    }

    private static boolean hasScreen(Icon icon) {
        BufferedImage image = ImageUtil.createImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            icon.paintIcon(null, graphics, 0, 0);
        } finally {
            graphics.dispose();
        }
        return (image.getRGB(icon.getIconWidth() / 2, 1) >>> 24) == 0xFF;
    }

    private static Icon[] pair(Icon actual, Icon expected) {
        return new Icon[]{actual, expected};
    }

    private static String pathOf(Icon icon) {
        assertInstanceOf(icon, CachedImageIcon.class);
        String path = ((CachedImageIcon) icon).getOriginalPath();
        assertNotNull(path);
        return path;
    }
}
