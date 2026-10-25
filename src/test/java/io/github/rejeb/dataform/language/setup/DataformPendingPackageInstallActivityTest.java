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
package io.github.rejeb.dataform.language.setup;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.containers.ContainerUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DataformPendingPackageInstallActivityTest extends BasePlatformTestCase {

    private final List<VirtualFile> installs = new ArrayList<>();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        ServiceContainerUtil.replaceService(getProject(), DataformPackageInstaller.class,
                installs::add, getTestRootDisposable());
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            PropertiesComponent.getInstance(getProject())
                    .setList(DataformPendingPackageInstallActivity.PENDING_DIRS_KEY, List.of());
        } finally {
            super.tearDown();
        }
    }

    public void testInstallsRightAwayWhenTheProjectIsAlreadyOpened() throws IOException {
        VirtualFile dir = myFixture.getTempDirFixture().findOrCreateDir("new_project");

        DataformPendingPackageInstallActivity.schedule(getProject(), dir);

        assertEquals(List.of(dir), installs);
        assertEmpty(ContainerUtil.notNullize(PropertiesComponent.getInstance(getProject())
                .getList(DataformPendingPackageInstallActivity.PENDING_DIRS_KEY)));
    }

    public void testSchedulingTheSameDirectoryTwiceInstallsItOncePerSchedule() throws IOException {
        VirtualFile dir = myFixture.getTempDirFixture().findOrCreateDir("new_project");

        DataformPendingPackageInstallActivity.schedule(getProject(), dir);
        DataformPendingPackageInstallActivity.schedule(getProject(), dir);

        assertEquals(List.of(dir, dir), installs);
    }
}
