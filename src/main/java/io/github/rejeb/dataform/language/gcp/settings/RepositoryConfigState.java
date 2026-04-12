package io.github.rejeb.dataform.language.gcp.settings;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class RepositoryConfigState {
    private @Nullable String repositoryConfigId;
    private @Nullable String label;
    private @Nullable String projectId;
    private @Nullable String repositoryId;
    private @Nullable String location;
    private @Nullable String selectedWorkspaceId;

    public RepositoryConfigState() {
    }

    public RepositoryConfigState(@NotNull String repositoryConfigId,
                                 @NotNull String label,
                                 @NotNull String projectId,
                                 @NotNull String repositoryId,
                                 @NotNull String location) {
        this.repositoryConfigId = repositoryConfigId;
        this.label = label;
        this.projectId = projectId;
        this.repositoryId = repositoryId;
        this.location = location;
    }

    public @Nullable String getRepositoryConfigId() {
        return repositoryConfigId;
    }

    public void setRepositoryConfigId(@NotNull String repositoryConfigId) {
        this.repositoryConfigId = repositoryConfigId;
    }

    public @Nullable String getLabel() {
        return label;
    }

    public void setLabel(@NotNull String label) {
        this.label = label;
    }

    public @Nullable String getProjectId() {
        return projectId;
    }

    public void setProjectId(@NotNull String projectId) {
        this.projectId = projectId;
    }

    public @Nullable String getRepositoryId() {
        return repositoryId;
    }

    public void setRepositoryId(@NotNull String repositoryId) {
        this.repositoryId = repositoryId;
    }

    public @Nullable String getLocation() {
        return location;
    }

    public void setLocation(@NotNull String location) {
        this.location = location;
    }

    public @Nullable String getSelectedWorkspaceId() {
        return selectedWorkspaceId;
    }

    public void setSelectedWorkspaceId(@Nullable String selectedWorkspaceId) {
        this.selectedWorkspaceId = selectedWorkspaceId;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        RepositoryConfigState that = (RepositoryConfigState) o;
        return Objects.equals(repositoryConfigId, that.repositoryConfigId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(repositoryConfigId);
    }
}
