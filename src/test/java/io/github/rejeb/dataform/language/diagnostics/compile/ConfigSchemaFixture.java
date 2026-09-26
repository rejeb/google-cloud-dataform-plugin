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
package io.github.rejeb.dataform.language.diagnostics.compile;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.ServiceContainerUtil;
import io.github.rejeb.dataform.language.setup.DataformInterpreterManager;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

/**
 * Makes the Dataform config schema available to a light test, from the {@code configs.proto} of
 * the test resources, the way the config completion tests do.
 */
public final class ConfigSchemaFixture {

    private static final String PROTO_RESOURCE = "/dataform/configs.proto";

    private ConfigSchemaFixture() {
    }

    public static void install(Project project, Disposable disposable) throws Exception {
        Path coreDir = Files.createTempDirectory("dataform-core");
        try (InputStream stream = ConfigSchemaFixture.class.getResourceAsStream(PROTO_RESOURCE)) {
            if (stream == null) throw new IllegalStateException("missing " + PROTO_RESOURCE);
            Files.write(coreDir.resolve("configs.proto"), stream.readAllBytes());
        }
        VirtualFile coreVirtualDir = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(coreDir);
        ServiceContainerUtil.replaceService(project, DataformInterpreterManager.class,
                new StubInterpreterManager(coreVirtualDir), disposable);
    }

    private record StubInterpreterManager(VirtualFile coreDir) implements DataformInterpreterManager {

        @Override
        public Optional<VirtualFile> dataformCorePath() {
            return Optional.of(coreDir);
        }

        @Override
        public String currentDataformCoreVersion() {
            return "3.0.0";
        }

        @Override
        public Optional<GeneralCommandLine> buildDataformCompileCommand() {
            return Optional.empty();
        }

        @Override
        public Optional<GeneralCommandLine> buildDataformCommand(@NotNull List<String> arguments) {
            return Optional.empty();
        }
    }
}
