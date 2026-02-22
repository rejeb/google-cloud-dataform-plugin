package io.github.rejeb.dataform.projectWizard;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleTypeManager;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class DataformModuleType extends ModuleType<DataformModuleBuilder> {
    private static final String ID = "DATAFORM_MODULE_TYPE";
    protected DataformModuleType() {
        super(ID);
    }

    public static DataformModuleType getInstance() {
        return (DataformModuleType) ModuleTypeManager.getInstance().findByID(ID);
    }

    @Override
    public @NotNull DataformModuleBuilder createModuleBuilder() {
        return new DataformModuleBuilder();
    }

    @Override
    public @NotNull @Nls(capitalization = Nls.Capitalization.Title) String getName() {
        return "Dataform Project";
    }

    @Override
    public @NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String getDescription() {
        return "Create dataform project";
    }

    @Override
    public @NotNull Icon getNodeIcon(boolean isOpened) {
        return AllIcons.Providers.BigQuery;
    }
}
