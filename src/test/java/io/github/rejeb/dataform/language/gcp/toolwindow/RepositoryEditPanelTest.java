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
package io.github.rejeb.dataform.language.gcp.toolwindow;

import com.google.api.gax.rpc.NotFoundException;
import com.google.api.gax.rpc.PermissionDeniedException;
import com.google.api.gax.rpc.StatusCode;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.TitledSeparator;
import io.github.rejeb.dataform.language.gcp.common.GcpApiException;
import io.github.rejeb.dataform.language.gcp.settings.DataformRepositoryConfig;
import io.github.rejeb.dataform.language.ui.FilteringComboBox;
import io.github.rejeb.dataform.language.ui.StatusLabel;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;

import javax.swing.text.JTextComponent;

public class RepositoryEditPanelTest extends BasePlatformTestCase {

    private static final DataformRepositoryConfig CONFIG =
            new DataformRepositoryConfig("id", "Sales", "my-project", "sales", "europe-west1", "");

    private static StatusCode code(StatusCode.Code value) {
        return new StatusCode() {
            @Override
            public Code getCode() {
                return value;
            }

            @Override
            public Object getTransportCode() {
                return value.getHttpStatusCode();
            }
        };
    }

    public void testTheDialogLeavesRoomForTheForm() {
        RepositoryEditPanel panel = new RepositoryEditPanel(getProject());
        panel.load(CONFIG);

        assertTrue("The form needs " + panel.getPreferredSize().width + " px, the dialog gives it "
                        + ManageRepositoriesDialog.formWidth() + " px",
                panel.getPreferredSize().width <= ManageRepositoriesDialog.formWidth());
    }

    public void testStatusUsesIcons() {
        RepositoryEditPanel panel = new RepositoryEditPanel(getProject());
        panel.load(CONFIG);

        panel.showResult(false, null);

        assertEquals(StatusLabel.Kind.OK, panel.status().kind());
        assertFalse(panel.createLink().isVisible());
    }

    public void testANotFoundRepositoryOffersToCreateIt() {
        RepositoryEditPanel panel = new RepositoryEditPanel(getProject());
        panel.load(CONFIG);
        NotFoundException notFound = new NotFoundException(new RuntimeException("sales"), code(StatusCode.Code.NOT_FOUND), false);

        panel.showResult(false, new GcpApiException("Repository sales not found", notFound));

        assertEquals(StatusLabel.Kind.ERROR, panel.status().kind());
        assertEquals("Repository sales not found", panel.status().getText());
        assertTrue(panel.createLink().isVisible());
        assertEquals("Create in Google Cloud", panel.createLink().getText());
    }

    public void testOtherFailuresAndANewRepositoryHideTheCreateLink() {
        RepositoryEditPanel panel = new RepositoryEditPanel(getProject());
        panel.load(CONFIG);
        NotFoundException notFound = new NotFoundException(new RuntimeException("sales"), code(StatusCode.Code.NOT_FOUND), false);
        panel.showResult(false, new GcpApiException("not found", notFound));

        panel.load(CONFIG);
        assertFalse(panel.createLink().isVisible());
        assertEquals(StatusLabel.Kind.NONE, panel.status().kind());

        PermissionDeniedException denied = new PermissionDeniedException(new RuntimeException("iam"), code(StatusCode.Code.PERMISSION_DENIED), false);
        panel.showResult(false, new GcpApiException("Permission denied", denied));
        assertFalse(panel.createLink().isVisible());
    }

    public void testACreatedRepositoryHidesTheCreateLink() {
        RepositoryEditPanel panel = new RepositoryEditPanel(getProject());
        panel.load(CONFIG);
        NotFoundException notFound = new NotFoundException(new RuntimeException("sales"), code(StatusCode.Code.NOT_FOUND), false);
        panel.showResult(false, new GcpApiException("not found", notFound));

        panel.showResult(true, null);

        assertEquals(StatusLabel.Kind.OK, panel.status().kind());
        assertFalse(panel.createLink().isVisible());
    }

    public void testTheLocationKeepsAFreeTypedRegion() {
        RepositoryEditPanel panel = new RepositoryEditPanel(getProject());

        panel.load(new DataformRepositoryConfig("id", "Sales", "my-project", "sales", "northamerica-south9", ""));

        assertEquals("northamerica-south9", panel.buildConfig("fallback").location());
    }

    public void testTheLocationFiltersAsYouType() {
        RepositoryEditPanel panel = new RepositoryEditPanel(getProject());
        panel.load(new DataformRepositoryConfig("id", "Sales", "my-project", "sales", "us-central1", ""));
        FilteringComboBox location = all(panel, FilteringComboBox.class).get(0);

        ((JTextComponent) location.getEditor().getEditorComponent()).setText("europe-west1");

        assertEquals(List.of("europe-west1", "europe-west10", "europe-west12"), shown(location));
        assertEquals("europe-west1", panel.buildConfig("fallback").location());
    }

    public void testTheServiceAccountFiltersAsYouType() {
        RepositoryEditPanel panel = new RepositoryEditPanel(getProject());

        List<FilteringComboBox> combos = all(panel, FilteringComboBox.class);

        assertEquals(2, combos.size());
        assertInstanceOf(combos.get(1), ServiceAccountComboBox.class);
    }

    private static List<String> shown(FilteringComboBox combo) {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < combo.getModel().getSize(); i++) {
            values.add(combo.getModel().getElementAt(i));
        }
        return values;
    }

    public void testTheFormIsGroupedUnderRepositoryAndAuthentication() {
        RepositoryEditPanel panel = new RepositoryEditPanel(getProject());

        List<String> groups = all(panel, TitledSeparator.class).stream().map(TitledSeparator::getText).toList();

        assertEquals(List.of("Repository", "Authentication"), groups);
    }

    private static <T extends Component> List<T> all(Container root, Class<T> type) {
        List<T> found = new ArrayList<>();
        for (Component child : root.getComponents()) {
            if (type.isInstance(child)) {
                found.add(type.cast(child));
            }
            if (child instanceof Container container) {
                found.addAll(all(container, type));
            }
        }
        return found;
    }
}
