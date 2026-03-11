package io.github.rejeb.dataform.language.service;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLKeyValue;

import java.util.Map;

public record WorkflowSettingsProperty(
        String name,
        @Nullable String value,
        @Nullable YAMLKeyValue yamlRef,
        @Nullable Map<String, WorkflowSettingsProperty> children) {
    public boolean hasChildren() {
        return children != null && !children.isEmpty();
    }
}