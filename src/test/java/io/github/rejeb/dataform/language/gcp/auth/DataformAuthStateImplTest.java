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
package io.github.rejeb.dataform.language.gcp.auth;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class DataformAuthStateImplTest extends BasePlatformTestCase {

    public void testASignInFinishingInTheBackgroundIsHeardWhileADialogIsOpen()
            throws ExecutionException, InterruptedException, TimeoutException {
        List<AuthStatus> heard = new ArrayList<>();
        ApplicationManager.getApplication().getMessageBus().connect(getTestRootDisposable())
                .subscribe(DataformAuthListener.TOPIC, heard::add);
        Object dialog = new Object();
        LaterInvocator.enterModal(dialog);
        try {
            ApplicationManager.getApplication()
                    .executeOnPooledThread(() -> DataformAuthState.getInstance().markAuthenticated())
                    .get(10, TimeUnit.SECONDS);
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

            assertEquals(List.of(AuthStatus.NONE), heard);
        } finally {
            LaterInvocator.leaveModal(dialog);
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        }
    }
}
