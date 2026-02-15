/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
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

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ColoredProcessHandler;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileTask;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.util.Key;
import io.github.rejeb.dataform.setup.DataformInterpreterManager;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public final class DataformCompileBeforeTask implements CompileTask {

    @Override
    public boolean execute(@NotNull CompileContext context) {
        Optional<GeneralCommandLine> cmd = context.getProject()
                .getService(DataformInterpreterManager.class)
                .getInterpreterCommand("compile");
        if (context.isAutomake() || cmd.isEmpty()) {
            return true;
        }

        context.addMessage(CompilerMessageCategory.INFORMATION, "Running: " + cmd.get().getCommandLineString(), null, -1, -1);

        ColoredProcessHandler handler;
        try {
            handler = new ColoredProcessHandler(cmd.get());
        } catch (Exception e) {
            context.addMessage(CompilerMessageCategory.ERROR, "Failed to start dataform: " + e.getMessage(), null, -1, -1);
            return false;
        }

        handler.addProcessListener(new ProcessListener() {
            @Override
            public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
                String text = event.getText();
                if (text == null || text.isEmpty()) return;

                // Optionnel: tu peux mapper STDERR en WARNING/ERROR selon ton besoin.
                CompilerMessageCategory category =
                        (outputType == ProcessOutputTypes.STDERR) ? CompilerMessageCategory.WARNING : CompilerMessageCategory.INFORMATION;

                // addMessage() attend une "url" de fichier si tu veux du cliquable; ici on met null.
                context.addMessage(category, text.trim(), null, -1, -1);
            }
        });

        handler.startNotify();
        handler.waitFor();

        Integer exitCode = handler.getExitCode();
        if (exitCode == null) exitCode = -1;

        if (exitCode != 0) {
            context.addMessage(CompilerMessageCategory.ERROR, "Dataform compile failed (exit=" + exitCode + ")", null, -1, -1);
            return false; // fait échouer "Build Project"
        }

        context.addMessage(CompilerMessageCategory.INFORMATION, "Dataform compile succeeded", null, -1, -1);
        return true;
    }

}
