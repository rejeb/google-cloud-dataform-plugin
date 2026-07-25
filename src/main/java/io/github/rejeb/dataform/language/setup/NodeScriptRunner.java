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

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Runs a JavaScript file with the Node interpreter configured for the project.
 */
public final class NodeScriptRunner {

    private static final Logger LOG = Logger.getInstance(NodeScriptRunner.class);

    private NodeScriptRunner() {
    }

    /**
     * Writes the given script to a cached temporary file, runs it with the project Node interpreter
     * while feeding {@code stdin}, and returns the captured output.
     *
     * @return empty when no Node interpreter is available or the process could not be started
     */
    public static Optional<ProcessOutput> run(@NotNull Project project,
                                              @NotNull String scriptName,
                                              @NotNull String script,
                                              @NotNull String stdin,
                                              int timeoutMs) {
        Path nodeBinDir = NodeInterpreterManager.getInstance(project).nodeBinDir();
        if (nodeBinDir == null) {
            LOG.info("No Node interpreter available, skipping script " + scriptName);
            return Optional.empty();
        }

        try {
            Path executable = nodeBinDir.resolve(SystemInfo.isWindows ? "node.exe" : "node");
            Path scriptFile = writeScript(scriptName, script);

            GeneralCommandLine cmd = new GeneralCommandLine()
                    .withExePath(executable.toString())
                    .withParameters(scriptFile.toString())
                    .withWorkDirectory(project.getBasePath())
                    .withCharset(StandardCharsets.UTF_8);
            cmd.withEnvironment("PATH", nodeBinDir + File.pathSeparator + System.getenv("PATH"));

            CapturingProcessHandler handler = new CapturingProcessHandler(cmd);
            handler.getProcessInput().write(stdin.getBytes(StandardCharsets.UTF_8));
            handler.getProcessInput().close();
            return Optional.of(handler.runProcess(timeoutMs));
        } catch (Exception e) {
            LOG.warn("Failed to run Node script " + scriptName, e);
            return Optional.empty();
        }
    }

    private static Path writeScript(@NotNull String scriptName, @NotNull String script) throws Exception {
        Path directory = Paths.get(PathManager.getTempPath());
        Path scriptFile = directory.resolve(scriptName + "-" + contentHash(script) + ".js");
        if (Files.exists(scriptFile)) {
            return scriptFile;
        }
        Files.createDirectories(directory);
        Path temp = Files.createTempFile(directory, scriptName, ".js.tmp");
        Files.writeString(temp, script, StandardCharsets.UTF_8);
        try {
            Files.move(temp, scriptFile, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException raceOrUnsupported) {
            Files.deleteIfExists(temp);
            if (!Files.exists(scriptFile)) {
                Files.writeString(scriptFile, script, StandardCharsets.UTF_8);
            }
        }
        return scriptFile;
    }

    static String contentHash(@NotNull String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
