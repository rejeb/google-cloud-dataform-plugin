package io.github.rejeb.dataform.language.setup;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.vfs.VirtualFile;
import io.github.rejeb.dataform.setup.DataformInterpreterManager;

import java.util.Optional;

public class DataformInterpreterManagerTestImpl implements DataformInterpreterManager {
    @Override
    public Optional<VirtualFile> dataformCorePath() {
        return Optional.empty();
    }

    @Override
    public Optional<VirtualFile> dataformCliDir() {
        return Optional.empty();
    }

    @Override
    public String currentDataformCoreVersion() {
        return "";
    }

    @Override
    public Optional<GeneralCommandLine> buildDataformCompileCommand() {
        return Optional.empty();
    }
}
