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

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DataformCoreVersionMigratorTest extends BasePlatformTestCase {

    private static final String SETTINGS = "defaultProject: my-project\n"
            + "defaultLocation: US\n"
            + "dataformCoreVersion: 3.0.26\n"
            + "defaultDataset: dataform\n";
    private static final String MIGRATED_SETTINGS = "defaultProject: my-project\n"
            + "defaultLocation: US\n"
            + "defaultDataset: dataform\n";

    private final List<VirtualFile> installs = new ArrayList<>();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        ServiceContainerUtil.replaceService(getProject(), DataformPackageInstaller.class,
                installs::add, getTestRootDisposable());
    }

    public void testFindsTheDeclaredCoreVersion() {
        VirtualFile settings = myFixture.addFileToProject("workflow_settings.yaml", SETTINGS).getVirtualFile();

        Optional<String> version = ReadAction.nonBlocking(() -> migrator().findDeclaredCoreVersion(settings))
                .executeSynchronously();

        assertEquals(Optional.of("3.0.26"), version);
    }

    public void testFindsNothingWhenTheVersionIsNotDeclared() {
        VirtualFile settings = myFixture.addFileToProject("workflow_settings.yaml", MIGRATED_SETTINGS)
                .getVirtualFile();

        Optional<String> version = ReadAction.nonBlocking(() -> migrator().findDeclaredCoreVersion(settings))
                .executeSynchronously();

        assertEquals(Optional.empty(), version);
    }

    public void testCreatesPackageJsonAndRemovesTheSetting() throws IOException {
        VirtualFile settings = myFixture.addFileToProject("workflow_settings.yaml", SETTINGS).getVirtualFile();

        assertTrue(migrator().migrate(settings));

        assertEquals(MIGRATED_SETTINGS, VfsUtilCore.loadText(settings));
        assertEquals(DataformPackageJson.content("3.0.26"), text("package.json"));
        assertEquals(List.of(settings.getParent()), installs);
    }

    public void testAddsTheDependenciesToAnExistingPackageJson() throws IOException {
        VirtualFile settings = myFixture.addFileToProject("workflow_settings.yaml", SETTINGS).getVirtualFile();
        myFixture.addFileToProject("package.json", "{\n  \"name\": \"pipelines\"\n}\n");

        assertTrue(migrator().migrate(settings));

        assertEquals("{\n  \"name\": \"pipelines\",\n  \"dependencies\": {\n    \"@dataform/core\": \"3.0.26\"\n  }\n}\n",
                text("package.json"));
        assertEquals(MIGRATED_SETTINGS, VfsUtilCore.loadText(settings));
    }

    public void testAddsTheCoreToExistingDependencies() throws IOException {
        VirtualFile settings = myFixture.addFileToProject("workflow_settings.yaml", SETTINGS).getVirtualFile();
        myFixture.addFileToProject("package.json",
                "{\n  \"dependencies\": {\n    \"lodash\": \"4.17.21\"\n  }\n}\n");

        assertTrue(migrator().migrate(settings));

        assertEquals("{\n  \"dependencies\": {\n    \"lodash\": \"4.17.21\",\n    \"@dataform/core\": \"3.0.26\"\n  }\n}\n",
                text("package.json"));
    }

    public void testKeepsTheCoreVersionAlreadyInPackageJson() throws IOException {
        VirtualFile settings = myFixture.addFileToProject("workflow_settings.yaml", SETTINGS).getVirtualFile();
        String packageJson = "{\n  \"dependencies\": {\n    \"@dataform/core\": \"3.0.40\"\n  }\n}\n";
        myFixture.addFileToProject("package.json", packageJson);

        assertTrue(migrator().migrate(settings));

        assertEquals(packageJson, text("package.json"));
        assertEquals(MIGRATED_SETTINGS, VfsUtilCore.loadText(settings));
    }

    public void testLeavesBothFilesUntouchedWhenDependenciesIsNotAnObject() throws IOException {
        VirtualFile settings = myFixture.addFileToProject("workflow_settings.yaml", SETTINGS).getVirtualFile();
        String packageJson = "{\n  \"dependencies\": []\n}\n";
        myFixture.addFileToProject("package.json", packageJson);

        assertFalse(migrator().migrate(settings));

        assertEquals(packageJson, text("package.json"));
        assertEquals(SETTINGS, VfsUtilCore.loadText(settings));
        assertTrue(installs.isEmpty());
    }

    private DataformCoreVersionMigrator migrator() {
        return DataformCoreVersionMigrator.getInstance(getProject());
    }

    @NotNull
    private String text(@NotNull String relativePath) throws IOException {
        VirtualFile file = myFixture.findFileInTempDir(relativePath);
        assertNotNull(relativePath + " not found", file);
        return VfsUtilCore.loadText(file);
    }
}
