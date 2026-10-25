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
package io.github.rejeb.dataform.language.columns.impact;

import com.intellij.build.DefaultBuildDescriptor;
import com.intellij.build.FilePosition;
import com.intellij.build.events.FileMessageEvent;
import com.intellij.build.events.MessageEvent;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.ThreeState;
import io.github.rejeb.dataform.language.diagnostics.sql.bigquery.DryRunBuildProblem;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ColumnImpactBuildViewTest extends BasePlatformTestCase {

    private static final ColumnChange EMAIL = new ColumnChange("proj.ds.customers", ColumnChange.Kind.RENAMED,
            "email", "customer_email", "STRING", "STRING");
    private static final ColumnChange ORDER_ID = new ColumnChange("proj.ds.orders", ColumnChange.Kind.REMOVED,
            "order_id", null, "INT64", null);

    public void testEachFailureIsListedUnderItsFileWithTheChangeBehindIt() throws Exception {
        Path goldPath = Files.createTempFile("gold", ".sqlx");
        goldPath.toFile().deleteOnExit();
        VirtualFile gold = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(goldPath);
        assertNotNull(gold);
        DryRunBuildProblem failure = new DryRunBuildProblem("proj.ds.gold", gold, 27, 35,
                "Name email not found inside c", "Name email not found inside c at [28:36]", "email");

        List<MessageEvent> events = ColumnImpactBuildView.messagesByFile(new Object(),
                List.of(new ColumnImpact(EMAIL, List.of(failure))));

        assertEquals(1, events.size());
        MessageEvent event = events.getFirst();
        assertInstanceOf(event, FileMessageEvent.class);
        assertEquals(new FilePosition(gold.toNioPath(), 27, 35), ((FileMessageEvent) event).getFilePosition());
        assertEquals(MessageEvent.Kind.ERROR, event.getKind());
        assertEquals("BigQuery: Name email not found inside c — Column `email` was renamed to `customer_email` in `customers`",
                event.getMessage());
    }

    public void testAChangeNoQueryFailsOnIsListedUnderTheBuild() {
        List<MessageEvent> events = ColumnImpactBuildView.messagesByFile(new Object(),
                List.of(new ColumnImpact(ORDER_ID, List.of())));

        assertEquals(1, events.size());
        assertFalse(events.getFirst() instanceof FileMessageEvent);
        assertEquals(MessageEvent.Kind.INFO, events.getFirst().getKind());
        assertEquals("Column `order_id` no longer exists in `orders`", events.getFirst().getMessage());
    }

    public void testTheReportDoesNotOpenTheFileOfItsFirstError() {
        DefaultBuildDescriptor descriptor = ColumnImpactBuildView.descriptor(new Object(), "/p");

        assertEquals(ThreeState.NO, descriptor.isNavigateToError());
        assertTrue(descriptor.isActivateToolWindowWhenAdded());
    }
}
