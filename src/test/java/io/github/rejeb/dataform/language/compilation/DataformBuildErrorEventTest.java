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
package io.github.rejeb.dataform.language.compilation;

import com.intellij.build.DefaultBuildDescriptor;
import com.intellij.build.FilePosition;
import com.intellij.build.events.FileMessageEvent;
import com.intellij.build.events.MessageEvent;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.ThreeState;

import java.io.File;

public class DataformBuildErrorEventTest extends BasePlatformTestCase {

    public void testAnErrorWithAFileIsGroupedUnderThatFile() {
        FilePosition position = new FilePosition(new File("/p/definitions/gold.sqlx"), 27, 35);

        MessageEvent event = DataformBuildManager.errorEvent(new Object(), "BigQuery: Name email not found inside c",
                "details", position);

        assertInstanceOf(event, FileMessageEvent.class);
        assertEquals(position, ((FileMessageEvent) event).getFilePosition());
        assertEquals(MessageEvent.Kind.ERROR, event.getKind());
    }

    public void testAnErrorWithoutAFileStaysUnderTheBuild() {
        MessageEvent event = DataformBuildManager.errorEvent(new Object(), "dataform compile returned no output",
                "details", null);

        assertFalse(event instanceof FileMessageEvent);
    }

    public void testTheBuildDoesNotOpenTheFileOfItsFirstError() {
        DefaultBuildDescriptor descriptor = DataformBuildManager.descriptor(new Object(), "/p", 0L);

        assertEquals(ThreeState.NO, descriptor.isNavigateToError());
    }
}
