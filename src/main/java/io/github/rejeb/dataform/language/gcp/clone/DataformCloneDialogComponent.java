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
package io.github.rejeb.dataform.language.gcp.clone;

import com.intellij.icons.AllIcons;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.CheckedDisposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.CheckoutProvider;
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogExtensionComponent;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.InlineBanner;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.util.Alarm;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import io.github.rejeb.dataform.language.gcp.auth.AuthStatus;
import io.github.rejeb.dataform.language.gcp.auth.DataformAuthListener;
import io.github.rejeb.dataform.language.gcp.auth.DataformAuthState;
import io.github.rejeb.dataform.language.gcp.auth.DataformCredentialsService;
import io.github.rejeb.dataform.language.gcp.auth.GcpAuthErrors;
import io.github.rejeb.dataform.language.gcp.common.DataformLocations;
import io.github.rejeb.dataform.language.gcp.common.GcpApiException;
import io.github.rejeb.dataform.language.gcp.workspace.DataformRepository;
import io.github.rejeb.dataform.language.gcp.workspace.Workspace;
import io.github.rejeb.dataform.language.gcp.workspace.repository.WorkspaceRepository;
import io.github.rejeb.dataform.language.ui.Banners;
import io.github.rejeb.dataform.language.ui.EmptyStates;
import io.github.rejeb.dataform.language.ui.FilteringComboBox;
import io.github.rejeb.dataform.language.ui.StatusLabel;
import io.github.rejeb.dataform.language.ui.UiText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * The Dataform tab of the Get from Version Control dialog: lists the repositories of a GCP project
 * and location, and fetches the chosen one from its Git remote or from one of its workspaces.
 * {@link #doClone} runs after the dialog closed, so it reads this component without relying on it
 * being alive.
 */
final class DataformCloneDialogComponent extends VcsCloneDialogExtensionComponent {

    static final String LAST_PROJECT_KEY = "dataform.clone.lastProject";
    static final String LAST_LOCATION_KEY = "dataform.clone.lastLocation";
    static final String LAST_PARENT_KEY = "dataform.clone.lastParent";

    private static final String DEFAULT_LOCATION = "us-central1";
    private static final int TYPING_PAUSE_MILLIS = 600;
    private static final String REPOSITORIES_CARD = "repositories";
    private static final String SIGN_IN_CARD = "signIn";
    private static final String SEARCH_REPOSITORIES = "Search repositories";

    private final Project project;
    private final ModalityState modality;
    private final WorkspaceRepository repository;
    private final DataformCredentialsService credentials;
    private final RepositoryCheckoutStarter starter;
    private final Executor background;
    private final RepositoryCloneForm form = new RepositoryCloneForm();

    private final CardLayout cards = new CardLayout();
    private final JPanel view = new JPanel(cards);
    private final JButton signInButton = new JButton("Sign in");
    private final StatusLabel signInStatus = new StatusLabel();
    private final JBTextField projectField = new JBTextField();
    private final FilteringComboBox locationField = new FilteringComboBox(DataformLocations.all());
    private final SearchTextField searchField = new SearchTextField(false);
    private final InlineBanner listingBanner = Banners.error("");
    private final CollectionListModel<DataformRepository> listModel = new CollectionListModel<>();
    private final JBList<DataformRepository> repositoryList = new JBList<>(listModel);
    private final JBRadioButton gitRemoteButton = new JBRadioButton("Git remote");
    private final JBRadioButton workspaceButton = new JBRadioButton("Workspace");
    private final JBLabel remoteLine = new JBLabel();
    private final JComponent remoteHelp = comment("Clones with your local Git credentials and keeps history and branches.");
    private final JComponent noRemoteHelp = comment("This repository has no Git remote.");
    private final CollectionComboBoxModel<String> workspaceModel = new CollectionComboBoxModel<>();
    private final ComboBox<String> workspaceCombo = new ComboBox<>(workspaceModel);
    private final JComponent workspaceHelp = comment("<html>Copies the files of this workspace, with no local Git history.<br>"
            + "Push and Pull stay in the Dataform tool window.</html>");
    private final StatusLabel workspaceStatus = new StatusLabel();
    private final CloneDirectoryField directory;
    private final Alarm typingPause;
    private final CheckedDisposable alive = Disposer.newCheckedDisposable();

    private List<DataformRepository> repositories = List.of();
    private @Nullable String listedProject;
    private @Nullable String listedLocation;
    private @Nullable String listingProblem;
    private long listingGeneration;
    private long workspaceGeneration;
    private boolean started;
    private boolean signedIn;
    private boolean showsSignIn;
    private boolean loadingWorkspaces;
    private boolean updatingList;
    private boolean updatingWorkspaces;

    DataformCloneDialogComponent(@NotNull Project project, @NotNull ModalityState modality,
                                 @NotNull WorkspaceRepository repository,
                                 @NotNull DataformCredentialsService credentials,
                                 @NotNull RepositoryCheckoutStarter starter, @NotNull Executor background) {
        this.project = project;
        this.modality = modality;
        this.repository = repository;
        this.credentials = credentials;
        this.starter = starter;
        this.background = background;
        this.typingPause = new Alarm(Alarm.ThreadToUse.SWING_THREAD, this);
        Disposer.register(this, alive);
        PropertiesComponent properties = PropertiesComponent.getInstance();
        this.directory = new CloneDirectoryField(project, properties.getValue(LAST_PARENT_KEY, ProjectUtil.getBaseDir()));
        projectField.setText(properties.getValue(LAST_PROJECT_KEY, ""));
        locationField.setText(properties.getValue(LAST_LOCATION_KEY, DEFAULT_LOCATION));
        view.add(repositoriesCard(), REPOSITORIES_CARD);
        view.add(signInCard(), SIGN_IN_CARD);
        listen();
        showWaitingForAccount();
        showSource();
    }

    @Override
    public @NotNull JComponent getView() {
        return view;
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
        return projectField.getText().isBlank() ? projectField : searchField.getTextEditor();
    }

    @Override
    public void onComponentSelected() {
        publishOkAction();
        if (!started) {
            started = true;
            checkAccount();
        }
    }

    @Override
    public @NotNull List<ValidationInfo> doValidateAll() {
        String problem = directory.problem();
        return problem == null ? List.of() : List.of(new ValidationInfo(problem, directory.component().getTextField()));
    }

    @Override
    public void doClone(@NotNull CheckoutProvider.Listener listener) {
        DataformRepository chosen = form.repository();
        if (chosen == null || !form.canProceed() || listedProject == null || listedLocation == null) {
            return;
        }
        String workspace = form.source() == RepositorySource.WORKSPACE ? form.selectedWorkspace() : null;
        RepositoryCheckoutRequest request = new RepositoryCheckoutRequest(listedProject, listedLocation, chosen,
                form.source(), workspace, directory.path());
        remember(request);
        starter.start(request, listener);
    }

    /**
     * Lists the repositories of the project and location currently typed, without waiting for a pause.
     */
    void reloadRepositories() {
        typingPause.cancelAllRequests();
        if (!signedIn) {
            return;
        }
        String gcpProject = projectField.getText().trim();
        String location = locationField.getText();
        long generation = ++listingGeneration;
        if (gcpProject.isEmpty() || location.isEmpty()) {
            repositoryList.setPaintBusy(false);
            listed(gcpProject, location, List.of());
            return;
        }
        repositoryList.setPaintBusy(true);
        EmptyStates.show(repositoryList.getEmptyText(), "Loading repositories…");
        background.execute(() -> {
            try {
                List<DataformRepository> found = repository.findRepositories(gcpProject, location);
                onUi(() -> {
                    if (generation == listingGeneration) {
                        repositoryList.setPaintBusy(false);
                        listed(gcpProject, location, found);
                    }
                });
            } catch (GcpApiException e) {
                onUi(() -> {
                    if (generation == listingGeneration) {
                        repositoryList.setPaintBusy(false);
                        listingFailed(e);
                    }
                });
            }
        });
    }

    boolean showsSignIn() {
        return showsSignIn;
    }

    @NotNull JButton signInButton() {
        return signInButton;
    }

    @NotNull JBList<DataformRepository> repositoryList() {
        return repositoryList;
    }

    @NotNull JBTextField projectField() {
        return projectField;
    }

    @NotNull FilteringComboBox locationField() {
        return locationField;
    }

    @NotNull SearchTextField searchField() {
        return searchField;
    }

    @NotNull JBRadioButton gitRemoteButton() {
        return gitRemoteButton;
    }

    @NotNull JBRadioButton workspaceButton() {
        return workspaceButton;
    }

    @NotNull ComboBox<String> workspaceCombo() {
        return workspaceCombo;
    }

    @NotNull StatusLabel workspaceStatus() {
        return workspaceStatus;
    }

    @NotNull TextFieldWithBrowseButton directoryField() {
        return directory.component();
    }

    @Nullable String listingProblem() {
        return listingProblem;
    }

    private void listen() {
        signInButton.addActionListener(e -> credentials.signIn(project.isDefault() ? null : project));
        ApplicationManager.getApplication().getMessageBus().connect(this)
                .subscribe(DataformAuthListener.TOPIC, (DataformAuthListener) this::authStatusChanged);
        DocumentListener originEdited = new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                typingPause.cancelAllRequests();
                typingPause.addRequest(DataformCloneDialogComponent.this::reloadRepositories, TYPING_PAUSE_MILLIS, modality);
            }
        };
        projectField.getDocument().addDocumentListener(originEdited);
        if (locationField.getEditor().getEditorComponent() instanceof JTextComponent editor) {
            editor.getDocument().addDocumentListener(originEdited);
        }
        searchField.addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                showRepositories();
            }
        });
        repositoryList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !updatingList) {
                repositoryChosen(repositoryList.getSelectedValue());
            }
        });
        gitRemoteButton.addActionListener(e -> sourceChosen(RepositorySource.GIT_REMOTE));
        workspaceButton.addActionListener(e -> sourceChosen(RepositorySource.WORKSPACE));
        workspaceCombo.addActionListener(e -> {
            if (!updatingWorkspaces) {
                form.selectWorkspace(workspaceModel.getSelected());
            }
        });
    }

    private void checkAccount() {
        background.execute(() -> {
            boolean accountFound = credentials.isSignedIn();
            if (accountFound) {
                credentials.getAccountEmail();
            }
            onUi(() -> accountChecked(accountFound));
        });
    }

    private void authStatusChanged(@NotNull AuthStatus status) {
        if (!started) {
            return;
        }
        switch (status) {
            case NONE -> checkAccount();
            case REQUIRED -> accountChecked(false);
            case IN_PROGRESS -> {
                signInButton.setEnabled(false);
                signInStatus.progress("Waiting for the sign-in in your browser…");
            }
        }
    }

    private void accountChecked(boolean accountFound) {
        signedIn = accountFound;
        showsSignIn = !accountFound;
        repositoryList.setPaintBusy(false);
        cards.show(view, accountFound ? REPOSITORIES_CARD : SIGN_IN_CARD);
        signInButton.setEnabled(true);
        String error = DataformAuthState.getInstance().getLastError();
        if (error == null) {
            signInStatus.clear();
        } else {
            signInStatus.error(error);
        }
        getDialogStateListener().onListItemChanged();
        okActionChanged();
        if (accountFound) {
            reloadRepositories();
        }
    }

    private void listed(@NotNull String gcpProject, @NotNull String location, @NotNull List<DataformRepository> found) {
        boolean sameOrigin = gcpProject.equals(listedProject) && location.equals(listedLocation);
        listedProject = gcpProject;
        listedLocation = location;
        repositories = found;
        showListingProblem(null);
        if (!sameOrigin) {
            repositoryChosen(null);
        }
        showRepositories();
    }

    private void listingFailed(@NotNull GcpApiException failure) {
        if (GcpAuthErrors.isAuthFailure(failure)) {
            accountChecked(false);
            return;
        }
        repositories = List.of();
        showListingProblem(StringUtil.notNullize(failure.getMessage(), "The repositories could not be listed."));
        showRepositories();
    }

    private void showListingProblem(@Nullable String problem) {
        listingProblem = problem;
        if (problem != null) {
            Banners.setMessage(listingBanner, problem);
        }
        listingBanner.setVisible(problem != null);
    }

    private void showRepositories() {
        String query = searchField.getText().trim();
        List<DataformRepository> shown = repositories.stream()
                .filter(candidate -> StringUtil.containsIgnoreCase(candidate.repositoryId(), query))
                .toList();
        DataformRepository chosen = form.repository();
        boolean chosenShown = chosen != null && shown.contains(chosen);
        updatingList = true;
        try {
            listModel.replaceAll(shown);
            if (chosenShown) {
                repositoryList.setSelectedValue(chosen, true);
            }
        } finally {
            updatingList = false;
        }
        showEmptyText();
        if (!chosenShown) {
            repositoryChosen(null);
        }
    }

    private void showEmptyText() {
        if (listingProblem != null) {
            repositoryList.getEmptyText().clear();
        } else if (StringUtil.isEmpty(listedProject)) {
            EmptyStates.show(repositoryList.getEmptyText(), "Enter a GCP project ID");
        } else if (StringUtil.isEmpty(listedLocation)) {
            EmptyStates.show(repositoryList.getEmptyText(), "Enter a location");
        } else if (repositories.isEmpty()) {
            EmptyStates.show(repositoryList.getEmptyText(), "No Dataform repository in " + listedLocation);
        } else {
            EmptyStates.show(repositoryList.getEmptyText(), "No repository matches this search in " + listedLocation);
        }
    }

    private void showWaitingForAccount() {
        repositoryList.setPaintBusy(true);
        EmptyStates.show(repositoryList.getEmptyText(), "Looking for your Google Cloud account…");
    }

    private void repositoryChosen(@Nullable DataformRepository chosen) {
        if (Objects.equals(chosen, form.repository())) {
            return;
        }
        workspaceGeneration++;
        loadingWorkspaces = false;
        form.selectRepository(chosen);
        directory.follow(chosen == null ? null : chosen.repositoryId());
        loadWorkspacesIfNeeded();
        showSource();
    }

    private void sourceChosen(@NotNull RepositorySource source) {
        form.selectSource(source);
        loadWorkspacesIfNeeded();
        showSource();
    }

    private void loadWorkspacesIfNeeded() {
        DataformRepository chosen = form.repository();
        if (!form.needsWorkspaces() || chosen == null || listedProject == null || listedLocation == null) {
            return;
        }
        form.workspacesRequested();
        loadingWorkspaces = true;
        long generation = ++workspaceGeneration;
        String gcpProject = listedProject;
        String location = listedLocation;
        background.execute(() -> {
            try {
                List<String> workspaces = repository.findAll(gcpProject, location, chosen.repositoryId()).stream()
                        .map(Workspace::workspaceId)
                        .toList();
                onUi(() -> {
                    if (generation == workspaceGeneration) {
                        loadingWorkspaces = false;
                        form.workspacesLoaded(workspaces);
                        showSource();
                    }
                });
            } catch (GcpApiException e) {
                onUi(() -> {
                    if (generation == workspaceGeneration) {
                        workspacesFailed(e);
                    }
                });
            }
        });
    }

    private void workspacesFailed(@NotNull GcpApiException failure) {
        loadingWorkspaces = false;
        form.workspacesFailed(StringUtil.notNullize(failure.getMessage(), "The workspaces could not be listed."));
        if (GcpAuthErrors.isAuthFailure(failure)) {
            accountChecked(false);
        }
        showSource();
    }

    private void showSource() {
        DataformRepository chosen = form.repository();
        boolean hasRemote = chosen != null && chosen.hasGitRemote();
        boolean fromWorkspace = chosen != null && form.source() == RepositorySource.WORKSPACE;
        gitRemoteButton.setEnabled(hasRemote);
        workspaceButton.setEnabled(chosen != null);
        (fromWorkspace ? workspaceButton : gitRemoteButton).setSelected(true);
        remoteLine.setVisible(hasRemote);
        remoteLine.setText(hasRemote ? UiText.dot(chosen.remoteDisplayName(), chosen.defaultBranch()) : "");
        remoteHelp.setVisible(hasRemote);
        noRemoteHelp.setVisible(chosen != null && !hasRemote);
        showWorkspaces(fromWorkspace);
        okActionChanged();
        view.revalidate();
        view.repaint();
    }

    private void showWorkspaces(boolean fromWorkspace) {
        List<String> workspaces = form.workspaces();
        updatingWorkspaces = true;
        try {
            if (!workspaces.equals(workspaceModel.getItems())) {
                workspaceModel.replaceAll(workspaces);
            }
            workspaceModel.setSelectedItem(form.selectedWorkspace());
        } finally {
            updatingWorkspaces = false;
        }
        workspaceCombo.setVisible(!workspaces.isEmpty());
        workspaceCombo.setEnabled(fromWorkspace && !workspaces.isEmpty());
        String problem = form.workspaceProblem();
        boolean showProblem = fromWorkspace && problem != null;
        workspaceHelp.setVisible(!showProblem);
        if (showProblem) {
            workspaceStatus.error(problem);
            workspaceStatus.setToolTipText(problem);
        } else if (fromWorkspace && loadingWorkspaces) {
            workspaceStatus.progress("Loading workspaces…");
        } else {
            workspaceStatus.clear();
        }
    }

    private void okActionChanged() {
        if (view.isVisible()) {
            publishOkAction();
        }
    }

    private void publishOkAction() {
        getDialogStateListener().onOkActionNameChanged(form.actionName());
        getDialogStateListener().onOkActionEnabled(signedIn && form.canProceed());
    }

    private void remember(@NotNull RepositoryCheckoutRequest request) {
        PropertiesComponent properties = PropertiesComponent.getInstance();
        properties.setValue(LAST_PROJECT_KEY, request.projectId());
        properties.setValue(LAST_LOCATION_KEY, request.location());
        Path parent = request.directory().getParent();
        if (parent != null) {
            properties.setValue(LAST_PARENT_KEY, parent.toString());
        }
    }

    private void onUi(@NotNull Runnable update) {
        ApplicationManager.getApplication().invokeLater(update, modality, ignored -> alive.isDisposed());
    }

    private @NotNull JComponent repositoriesCard() {
        repositoryList.setCellRenderer(new RepositoryListRenderer());
        repositoryList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        repositoryList.getAccessibleContext().setAccessibleName("Dataform repositories");
        repositoryList.setExpandableItemsEnabled(false);
        searchField.getTextEditor().getEmptyText().setText(SEARCH_REPOSITORIES);
        searchField.getTextEditor().getAccessibleContext().setAccessibleName(SEARCH_REPOSITORIES);
        listingBanner.setVisible(false);
        listingBanner.addAction("Retry", this::reloadRepositories);

        JPanel north = new JPanel(new VerticalLayout(JBUI.scale(8)));
        north.add(originRow());
        north.add(searchField);
        north.add(listingBanner);

        JPanel south = new JPanel(new VerticalLayout(JBUI.scale(12)));
        south.add(sourceSection());
        south.add(directoryRow());

        JPanel card = new JPanel(new BorderLayout(0, JBUI.scale(8)));
        card.setBorder(JBUI.Borders.empty(16));
        card.add(north, BorderLayout.NORTH);
        card.add(ScrollPaneFactory.createScrollPane(repositoryList), BorderLayout.CENTER);
        card.add(south, BorderLayout.SOUTH);
        return card;
    }

    private @NotNull JComponent originRow() {
        JPanel row = new JPanel(new GridBagLayout());
        GridBag place = new GridBag().setDefaultAnchor(GridBagConstraints.WEST);
        row.add(labelFor("GCP project:", projectField), place.next().insetRight(8));
        row.add(projectField, place.next().weightx(1).fillCellHorizontally());
        row.add(labelFor("Location:", locationField), place.next().insets(0, 12, 0, 8));
        row.add(locationField, place.next());
        return row;
    }

    private @NotNull JComponent sourceSection() {
        ButtonGroup group = new ButtonGroup();
        group.add(gitRemoteButton);
        group.add(workspaceButton);
        workspaceCombo.getAccessibleContext().setAccessibleName("Workspace");

        JBLabel title = new JBLabel("Get files from");
        title.setFont(JBFont.label().asBold());

        JPanel workspaceRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        workspaceRow.add(workspaceButton);
        workspaceRow.add(Box.createHorizontalStrut(JBUI.scale(8)));
        workspaceRow.add(workspaceCombo);

        JPanel section = new JPanel(new VerticalLayout(JBUI.scale(4)));
        section.add(title);
        section.add(gitRemoteButton);
        section.add(indented(remoteLine, remoteHelp, noRemoteHelp));
        section.add(workspaceRow);
        section.add(indented(workspaceHelp, workspaceStatus));
        return section;
    }

    private @NotNull JComponent directoryRow() {
        JPanel row = new JPanel(new GridBagLayout());
        GridBag place = new GridBag().setDefaultAnchor(GridBagConstraints.WEST);
        row.add(labelFor("Directory:", directory.component().getTextField()), place.next().insetRight(8));
        row.add(directory.component(), place.next().weightx(1).fillCellHorizontally());
        return row;
    }

    private @NotNull JComponent signInCard() {
        JBLabel title = new JBLabel("Sign in to Google Cloud");
        title.setFont(JBFont.h3().asBold());
        JBLabel explanation = new JBLabel("<html><div style='text-align:center'>Sign in to list your Dataform "
                + "repositories.<br>The Dataform tool window and BigQuery use the same account.</div></html>");
        explanation.setForeground(UIUtil.getContextHelpForeground());

        JPanel card = new JPanel(new GridBagLayout());
        GridBag place = new GridBag().setDefaultAnchor(GridBagConstraints.CENTER);
        card.add(new JBLabel(IconUtil.scale(AllIcons.General.User, title, 2f)), place.nextLine().next().insetBottom(12));
        card.add(title, place.nextLine().next().insetBottom(8));
        card.add(explanation, place.nextLine().next().insetBottom(12));
        card.add(signInButton, place.nextLine().next().insetBottom(8));
        card.add(signInStatus, place.nextLine().next());
        card.setBorder(JBUI.Borders.empty(32));
        return card;
    }

    private static @NotNull JBLabel labelFor(@NotNull String text, @NotNull JComponent field) {
        JBLabel label = new JBLabel(text);
        label.setLabelFor(field);
        return label;
    }

    private static @NotNull JComponent indented(@NotNull JComponent... lines) {
        JPanel panel = new JPanel(new VerticalLayout(JBUI.scale(2)));
        panel.setBorder(JBUI.Borders.emptyLeft(24));
        for (JComponent line : lines) {
            panel.add(line);
        }
        return panel;
    }

    private static @NotNull JComponent comment(@NotNull String text) {
        JBLabel comment = new JBLabel(text);
        comment.setFont(JBFont.medium());
        comment.setForeground(UIUtil.getContextHelpForeground());
        return comment;
    }
}
