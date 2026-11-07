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

import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ToolWindowEP;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.AnimatedIcon;
import com.intellij.ui.icons.CachedImageIcon;
import com.intellij.util.ui.ImageUtil;

import javax.swing.Icon;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Map;

public class DataformIconsTest extends BasePlatformTestCase {

    public void testEveryIconLoadsAt16Pixels() {
        Map<String, Icon> icons = Map.ofEntries(
                Map.entry("FILE", DataformIcons.FILE),
                Map.entry("FILE_DISABLED", DataformIcons.FILE_DISABLED),
                Map.entry("TEST_FILE", DataformIcons.TEST_FILE),
                Map.entry("TOOL_WINDOW", DataformIcons.TOOL_WINDOW),
                Map.entry("LAYOUT_EDITOR_ONLY", DataformIcons.LAYOUT_EDITOR_ONLY),
                Map.entry("LAYOUT_EDITOR_AND_PREVIEW", DataformIcons.LAYOUT_EDITOR_AND_PREVIEW),
                Map.entry("LAYOUT_PREVIEW_ONLY", DataformIcons.LAYOUT_PREVIEW_ONLY),
                Map.entry("PREVIEW_QUERY", DataformIcons.PREVIEW_QUERY),
                Map.entry("PREVIEW_SCHEMA", DataformIcons.PREVIEW_SCHEMA),
                Map.entry("LINEAGE", DataformIcons.LINEAGE),
                Map.entry("PREVIEW_TEST", DataformIcons.PREVIEW_TEST),
                Map.entry("COMPILE", DataformIcons.COMPILE),
                Map.entry("RUN", DataformIcons.RUN),
                Map.entry("QUERIES", DataformIcons.QUERIES),
                Map.entry("STATUS_SUCCEEDED", DataformIcons.STATUS_SUCCEEDED),
                Map.entry("STATUS_FAILED", DataformIcons.STATUS_FAILED),
                Map.entry("STATUS_CANCELLED", DataformIcons.STATUS_CANCELLED),
                Map.entry("STATUS_SKIPPED", DataformIcons.STATUS_SKIPPED),
                Map.entry("STATUS_PENDING", DataformIcons.STATUS_PENDING),
                Map.entry("FAILED_ONLY", DataformIcons.FAILED_ONLY),
                Map.entry("SPINNER", DataformIcons.SPINNER),
                Map.entry("STOP", DataformIcons.STOP),
                Map.entry("TRASH", DataformIcons.TRASH),
                Map.entry("DOWNLOAD", DataformIcons.DOWNLOAD),
                Map.entry("PAGE_FIRST", DataformIcons.PAGE_FIRST),
                Map.entry("PAGE_PREVIOUS", DataformIcons.PAGE_PREVIOUS),
                Map.entry("PAGE_NEXT", DataformIcons.PAGE_NEXT),
                Map.entry("PAGE_LAST", DataformIcons.PAGE_LAST),
                Map.entry("ACTION_TABLE", DataformIcons.ACTION_TABLE),
                Map.entry("ACTION_ASSERTION", DataformIcons.ACTION_ASSERTION),
                Map.entry("ACTION_OPERATIONS", DataformIcons.ACTION_OPERATIONS),
                Map.entry("COPY", DataformIcons.COPY),
                Map.entry("EXTERNAL_LINK", DataformIcons.EXTERNAL_LINK),
                Map.entry("REPOSITORY", DataformIcons.REPOSITORY),
                Map.entry("BRANCH", DataformIcons.BRANCH),
                Map.entry("PULL", DataformIcons.PULL),
                Map.entry("PUSH", DataformIcons.PUSH),
                Map.entry("ADD", DataformIcons.ADD),
                Map.entry("MANAGE_REPOSITORIES", DataformIcons.MANAGE_REPOSITORIES),
                Map.entry("ACCOUNT", DataformIcons.ACCOUNT),
                Map.entry("DIFF", DataformIcons.DIFF),
                Map.entry("FETCH", DataformIcons.FETCH));
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
        assertTrue(pathOf(DataformIcons.LAYOUT_EDITOR_ONLY).endsWith("/icons/layoutEditorOnly.svg"));
        assertTrue(pathOf(DataformIcons.LAYOUT_EDITOR_AND_PREVIEW).endsWith("/icons/layoutEditorAndPreview.svg"));
        assertTrue(pathOf(DataformIcons.LAYOUT_PREVIEW_ONLY).endsWith("/icons/layoutPreviewOnly.svg"));
        assertTrue(pathOf(DataformIcons.PREVIEW_QUERY).endsWith("/icons/previewQuery.svg"));
        assertTrue(pathOf(DataformIcons.PREVIEW_SCHEMA).endsWith("/icons/previewSchema.svg"));
        assertTrue(pathOf(DataformIcons.PREVIEW_TEST).endsWith("/icons/previewTest.svg"));
    }

    public void testLineageIconIsTheSiteColouredCardsSvg() {
        assertTrue(pathOf(DataformIcons.LINEAGE).endsWith("/icons/lineage.svg"));
    }

    public void testEditorRunIconsAreThePrototypeSvgs() {
        assertTrue(pathOf(DataformIcons.COMPILE).endsWith("/icons/compile.svg"));
        assertTrue(pathOf(DataformIcons.RUN).endsWith("/icons/run.svg"));
    }

    public void testServicesQueriesIconIsThePrototypeSvg() {
        assertTrue(pathOf(DataformIcons.QUERIES).endsWith("/icons/queries.svg"));
    }

    public void testServicesIconsAreThePrototypeSvgs() {
        assertTrue(pathOf(DataformIcons.STATUS_SUCCEEDED).endsWith("/icons/statusSucceeded.svg"));
        assertTrue(pathOf(DataformIcons.STATUS_FAILED).endsWith("/icons/statusFailed.svg"));
        assertTrue(pathOf(DataformIcons.STATUS_CANCELLED).endsWith("/icons/statusCancelled.svg"));
        assertTrue(pathOf(DataformIcons.STOP).endsWith("/icons/stop.svg"));
        assertTrue(pathOf(DataformIcons.TRASH).endsWith("/icons/trash.svg"));
        assertTrue(pathOf(DataformIcons.DOWNLOAD).endsWith("/icons/download.svg"));
        assertTrue(pathOf(DataformIcons.PAGE_FIRST).endsWith("/icons/pageFirst.svg"));
        assertTrue(pathOf(DataformIcons.PAGE_PREVIOUS).endsWith("/icons/pagePrevious.svg"));
        assertTrue(pathOf(DataformIcons.PAGE_NEXT).endsWith("/icons/pageNext.svg"));
        assertTrue(pathOf(DataformIcons.PAGE_LAST).endsWith("/icons/pageLast.svg"));
    }

    public void testRunConsoleIconsAreThePrototypeSvgs() {
        assertTrue(pathOf(DataformIcons.STATUS_SKIPPED).endsWith("/icons/statusSkipped.svg"));
        assertTrue(pathOf(DataformIcons.STATUS_PENDING).endsWith("/icons/statusPending.svg"));
        assertTrue(pathOf(DataformIcons.FAILED_ONLY).endsWith("/icons/failedOnly.svg"));
    }

    public void testQueryPreviewIconsAreThePrototypeSvgs() {
        assertTrue(pathOf(DataformIcons.ACTION_TABLE).endsWith("/icons/actionTable.svg"));
        assertTrue(pathOf(DataformIcons.ACTION_ASSERTION).endsWith("/icons/actionAssertion.svg"));
        assertTrue(pathOf(DataformIcons.ACTION_OPERATIONS).endsWith("/icons/actionOperations.svg"));
        assertTrue(pathOf(DataformIcons.COPY).endsWith("/icons/copy.svg"));
        assertTrue(pathOf(DataformIcons.EXTERNAL_LINK).endsWith("/icons/externalLink.svg"));
    }

    public void testDataformToolsIconsAreThePrototypeSvgs() {
        assertTrue(pathOf(DataformIcons.REPOSITORY).endsWith("/icons/repository.svg"));
        assertTrue(pathOf(DataformIcons.BRANCH).endsWith("/icons/branch.svg"));
        assertTrue(pathOf(DataformIcons.PULL).endsWith("/icons/pull.svg"));
        assertTrue(pathOf(DataformIcons.PUSH).endsWith("/icons/push.svg"));
        assertTrue(pathOf(DataformIcons.ADD).endsWith("/icons/add.svg"));
        assertTrue(pathOf(DataformIcons.MANAGE_REPOSITORIES).endsWith("/icons/manageRepositories.svg"));
        assertTrue(pathOf(DataformIcons.ACCOUNT).endsWith("/icons/account.svg"));
        assertTrue(pathOf(DataformIcons.DIFF).endsWith("/icons/diff.svg"));
        assertTrue(pathOf(DataformIcons.FETCH).endsWith("/icons/fetch.svg"));
    }

    public void testTheSpinnerTurnsThePrototypeSpinnerSvg() {
        assertInstanceOf(DataformIcons.SPINNER, AnimatedIcon.class);
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

    private static String pathOf(Icon icon) {
        assertInstanceOf(icon, CachedImageIcon.class);
        String path = ((CachedImageIcon) icon).getOriginalPath();
        assertNotNull(path);
        return path;
    }
}
