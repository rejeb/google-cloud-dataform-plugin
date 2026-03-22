package io.github.rejeb.dataform.language.gcp.execution.workflow.runconfig;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import javax.swing.*;
import java.awt.*;

public class SqlxRunOptionsPopup {

    public interface RunOptionsCallback {
        void run(boolean includeDependencies,
                 boolean includeDependants,
                 boolean fullRefresh);
    }

    public static void show(@Nullable Project project,
                            @NotNull RunOptionsCallback callback) {
        JBPopup[] popupRef = buildPopup(callback);

        if (project != null) {
            popupRef[0].show(new RelativePoint(MouseInfo.getPointerInfo().getLocation()));

        } else {
            popupRef[0].showInFocusCenter();
        }
    }

    private static JBPopup @NonNull [] buildPopup(@NonNull RunOptionsCallback callback) {
        JBCheckBox depsCheck = new JBCheckBox("Include dependencies");
        JBCheckBox dependantsCheck = new JBCheckBox("Include dependants");
        JBCheckBox fullRefreshCheck = new JBCheckBox("Full refresh");

        JButton runButton = new JButton("Run", AllIcons.Actions.Execute);
        runButton.putClientProperty("JButton.buttonType", "default");

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(UIUtil.getPanelBackground());
        panel.setBorder(JBUI.Borders.empty(8, 12));

        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0;
        gc.anchor = GridBagConstraints.WEST;
        gc.insets = JBUI.insets(2, 0);
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1.0;

        gc.gridy = 0;
        panel.add(depsCheck, gc);
        gc.gridy = 1;
        panel.add(dependantsCheck, gc);
        gc.gridy = 2;
        panel.add(fullRefreshCheck, gc);
        gc.gridy = 3;
        gc.insets = JBUI.insets(8, 0, 2, 0);
        gc.anchor = GridBagConstraints.EAST;
        gc.fill = GridBagConstraints.NONE;
        panel.add(runButton, gc);

        JBPopup[] popupRef = new JBPopup[1];
        popupRef[0] = JBPopupFactory.getInstance()
                .createComponentPopupBuilder(panel, depsCheck)
                .setTitle("Run Options")
                .setMovable(true)
                .setResizable(false)
                .setRequestFocus(true)
                .createPopup();

        runButton.addActionListener(e -> {
            popupRef[0].closeOk(null);
            callback.run(
                    depsCheck.isSelected(),
                    dependantsCheck.isSelected(),
                    fullRefreshCheck.isSelected()
            );
        });
        return popupRef;
    }
}