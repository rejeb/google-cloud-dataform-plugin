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

import com.intellij.javascript.nodejs.npm.NpmManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Resolving npm reads the Node interpreter from disk, which the platform cancels when a write
 * action is waiting: the cancellation belongs to the caller and must never be logged.
 */
public class NodeJsNpmUtilsTest extends BasePlatformTestCase {

    public void testCancellationWhileResolvingNpmReachesTheCaller() {
        NpmManager npmManager = mock(NpmManager.class);
        when(npmManager.getPackage()).thenThrow(new ProcessCanceledException());
        ServiceContainerUtil.replaceService(getProject(), NpmManager.class, npmManager, getTestRootDisposable());

        assertThrows(ProcessCanceledException.class, () -> NodeJsNpmUtils.findValidNpmPath(getProject()));
    }

    public void testReadActionYieldingToAWriteActionReachesTheCaller() {
        NpmManager npmManager = mock(NpmManager.class);
        when(npmManager.getPackage()).thenThrow(new ReadAction.CannotReadException());
        ServiceContainerUtil.replaceService(getProject(), NpmManager.class, npmManager, getTestRootDisposable());

        assertThrows(ReadAction.CannotReadException.class, () -> NodeJsNpmUtils.findValidNpmPath(getProject()));
    }
}
