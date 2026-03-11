package io.github.rejeb.dataform.setup;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.Optional;

public interface DataformInterpreterManager {
    Optional<VirtualFile> dataformCorePath();
    Optional<VirtualFile> dataformCliDir();
    String currentDataformCoreVersion();
    Optional<GeneralCommandLine> buildDataformCompileCommand();
}
