package io.github.rejeb.dataform.language.schema.sql;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;


public record ColumnInfo(
        @NotNull String name,
        @NotNull String type,
        @NotNull String mode,
        @NotNull List<ColumnInfo> subFields
) {
    public ColumnInfo(@NotNull String name, @NotNull String type, @NotNull String mode) {
        this(name, type, mode, Collections.emptyList());
    }



    public boolean isRecord() {
        return "RECORD".equals(type) || "STRUCT".equals(type);
    }

    public boolean isRepeated() {
        return "REPEATED".equals(mode);
    }

    @Override
    public String toString() {
        return "ColumnInfo{" +
                "name='" + name + '\'' +
                ", type='" + type + '\'' +
                ", mode='" + mode + '\'' +
                '}';
    }
}
