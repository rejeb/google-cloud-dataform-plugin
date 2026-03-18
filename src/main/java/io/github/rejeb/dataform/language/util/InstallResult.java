package io.github.rejeb.dataform.language.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record InstallResult(boolean success, @Nullable String errorMessage) {
    public static InstallResult ok() {
        return new InstallResult(true, null);
    }
    public static InstallResult error(@NotNull String message) {
        return new InstallResult(false, message);
    }
}
