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
package io.github.rejeb.dataform.language.lineage.view;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import com.intellij.ui.EditorNotificationPanel;
import io.github.rejeb.dataform.language.lineage.dataplex.DataplexLineageException.Kind;
import io.github.rejeb.dataform.language.lineage.dataplex.DataplexLineageService;
import io.github.rejeb.dataform.language.lineage.dataplex.LineageScope;
import io.github.rejeb.dataform.language.lineage.dataplex.TimeRange;
import io.github.rejeb.dataform.language.lineage.view.DataplexStateBanner.State;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DataplexStateBannerTest extends BasePlatformTestCase {

    private final List<String> calls = new ArrayList<>();

    private DataplexStateBanner banner(State state) {
        return DataplexStateBanner.of(getProject(), state, "sales-prod", TimeRange.LAST_7_DAYS, "boom",
                () -> calls.add("retry"), () -> calls.add("dataform"));
    }

    public void testApiDisabledIsAWarningWithEnableTheApi() {
        DataplexStateBanner banner = banner(State.API_DISABLED);

        assertEquals("The Data Lineage API is not enabled in sales-prod", banner.title());
        assertEquals("Knowledge Catalog records lineage only after the API is enabled, and only for jobs that run afterwards.",
                banner.message());
        assertEquals(EditorNotificationPanel.Status.Warning, banner.status());
        assertEquals(List.of("Enable the API", "Retry", "Show Dataform lineage"), banner.actions());
    }

    public void testPermissionDeniedIsAnErrorNamingTheRole() {
        DataplexStateBanner banner = banner(State.PERMISSION_DENIED);

        assertEquals("Your account cannot read lineage in sales-prod", banner.title());
        assertEquals("Ask for the role roles/datalineage.viewer on this project, and on each project whose tables you want to see.",
                banner.message());
        assertEquals(EditorNotificationPanel.Status.Error, banner.status());
        assertEquals(List.of("Copy the role name", "Retry", "Show Dataform lineage"), banner.actions());
    }

    public void testNothingRecordedIsAnInformationNamingTheRange() {
        DataplexStateBanner banner = banner(State.NOTHING_RECORDED);

        assertEquals("No lineage recorded in the last 7 days", banner.title());
        assertEquals("Lineage appears after a job runs, within 24 hours. Knowledge Catalog keeps it for 30 days.",
                banner.message());
        assertEquals(EditorNotificationPanel.Status.Info, banner.status());
        assertEquals(List.of("Retry", "Show Dataform lineage"), banner.actions());
    }

    public void testNotSignedInIsAWarningWithSignIn() {
        DataplexStateBanner banner = banner(State.NOT_SIGNED_IN);

        assertEquals("Sign in to Google Cloud to read the Knowledge Catalog lineage", banner.title());
        assertEquals(EditorNotificationPanel.Status.Warning, banner.status());
        assertEquals(List.of("Sign in", "Retry", "Show Dataform lineage"), banner.actions());
    }

    public void testAnyOtherFailureShowsItsMessage() {
        DataplexStateBanner banner = banner(State.FAILED);

        assertEquals("Knowledge Catalog cannot answer", banner.title());
        assertEquals("boom", banner.message());
        assertEquals(EditorNotificationPanel.Status.Error, banner.status());
        assertEquals(List.of("Retry", "Show Dataform lineage"), banner.actions());
    }

    public void testRetryAndShowDataformRunTheirHandlers() {
        DataplexStateBanner banner = banner(State.NOTHING_RECORDED);

        banner.run("Retry");
        banner.run("Show Dataform lineage");

        assertEquals(List.of("retry", "dataform"), calls);
    }

    public void testEachFailureKindHasItsState() {
        assertEquals(State.API_DISABLED, DataplexStateBanner.stateOf(Kind.API_DISABLED));
        assertEquals(State.PERMISSION_DENIED, DataplexStateBanner.stateOf(Kind.PERMISSION_DENIED));
        assertEquals(State.NOT_SIGNED_IN, DataplexStateBanner.stateOf(Kind.NOT_SIGNED_IN));
        assertEquals(State.FAILED, DataplexStateBanner.stateOf(Kind.OTHER));
    }

    public void testTheBannerShowsItsTitleInBoldAboveTheMessage() {
        assertNotNull(banner(State.API_DISABLED).component());
    }

    public void testWithoutCompiledGraphTheProjectIsThisProject() {
        assertEquals("this project", DataplexStateBanner.projectIdOf(mock(DataplexLineageService.class), null));
    }

    public void testAnUnreadableScopeGivesThisProject() {
        DataplexLineageService service = mock(DataplexLineageService.class);
        CompiledGraph compiled = mock(CompiledGraph.class);
        when(service.scopeOf(compiled)).thenThrow(new IllegalStateException("no scope"));

        assertEquals("this project", DataplexStateBanner.projectIdOf(service, compiled));
    }

    public void testTheProjectIsTheScopeProject() {
        DataplexLineageService service = mock(DataplexLineageService.class);
        CompiledGraph compiled = mock(CompiledGraph.class);
        when(service.scopeOf(compiled)).thenReturn(new LineageScope("acme", "EU"));

        assertEquals("acme", DataplexStateBanner.projectIdOf(service, compiled));
    }
}
