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
package io.github.rejeb.dataform.language.gcp.execution.workflow.runconfig.ui;

import com.intellij.ui.SimpleColoredComponent;
import org.jetbrains.annotations.NotNull;

import javax.swing.AbstractButton;
import javax.swing.JLabel;
import java.awt.Component;
import java.awt.Container;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * The text a row of labels and links shows, read from its visible children in the order they are
 * laid out.
 */
final class ShownText {

    private ShownText() {
    }

    /**
     * The texts of the visible children of a row, in order, joined by single spaces.
     */
    @NotNull
    static String of(@NotNull Container row) {
        return Arrays.stream(row.getComponents())
                .filter(Component::isVisible)
                .map(ShownText::textOf)
                .filter(text -> !text.isBlank())
                .collect(Collectors.joining(" "))
                .replaceAll("\\s+", " ")
                .strip();
    }

    @NotNull
    private static String textOf(@NotNull Component component) {
        return switch (component) {
            case SimpleColoredComponent colored -> colored.getCharSequence(false).toString();
            case JLabel label -> label.getText() != null ? label.getText() : "";
            case AbstractButton button -> button.getText() != null ? button.getText() : "";
            default -> "";
        };
    }
}
