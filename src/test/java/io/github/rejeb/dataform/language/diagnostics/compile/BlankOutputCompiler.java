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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.util.ui.UIUtil;
import io.github.rejeb.dataform.language.compilation.DataformCompilationService;
import io.github.rejeb.dataform.language.setup.DataformInterpreterManager;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Future;

/**
 * Runs a real compilation through the compilation service, with a command that prints nothing on
 * its standard output, as the Dataform CLI does when it fails before compiling anything. The
 * service then keeps its graph and replaces the graph errors with what the command printed on
 * its error output. The command is the running Java, so it exists on every platform.
 */
public final class BlankOutputCompiler {

    private BlankOutputCompiler() {
    }

    /**
     * Makes the compilations of the project run the blank-output command.
     */
    public static void install(Project project, Disposable disposable) {
        ServiceContainerUtil.replaceService(project, DataformInterpreterManager.class,
                new JavaVersionInterpreter(), disposable);
    }

    /**
     * Compiles the way a build does, off the event thread, dispatching the events the compilation
     * waits for until it is done.
     */
    public static void compileInBackground(Project project) throws Exception {
        Future<?> compilation = ApplicationManager.getApplication().executeOnPooledThread(
                () -> DataformCompilationService.getInstance(project).compile(true));
        while (!compilation.isDone()) {
            UIUtil.dispatchAllInvocationEvents();
            Thread.sleep(10);
        }
        compilation.get();
        UIUtil.dispatchAllInvocationEvents();
    }

    private record JavaVersionInterpreter() implements DataformInterpreterManager {

        @Override
        public Optional<VirtualFile> dataformCorePath() {
            return Optional.empty();
        }

        @Override
        public String currentDataformCoreVersion() {
            return "3.0.0";
        }

        @Override
        public Optional<GeneralCommandLine> buildDataformCompileCommand() {
            return Optional.of(new GeneralCommandLine(
                    ProcessHandle.current().info().command().orElseThrow(), "-version"));
        }

        @Override
        public Optional<GeneralCommandLine> buildDataformCommand(@NotNull List<String> arguments) {
            return Optional.empty();
        }
    }
}
