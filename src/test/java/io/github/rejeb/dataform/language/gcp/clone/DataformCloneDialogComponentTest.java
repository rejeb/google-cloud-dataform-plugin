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

import com.google.auth.oauth2.GoogleCredentials;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.openapi.vcs.CheckoutProvider;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogComponentStateListener;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.gcp.auth.AuthStatus;
import io.github.rejeb.dataform.language.gcp.auth.DataformAuthListener;
import io.github.rejeb.dataform.language.gcp.auth.DataformCredentialsService;
import io.github.rejeb.dataform.language.gcp.auth.GcpAuthRequiredException;
import io.github.rejeb.dataform.language.gcp.common.GcpApiException;
import io.github.rejeb.dataform.language.gcp.workspace.DataformRepository;
import io.github.rejeb.dataform.language.gcp.workspace.Workspace;
import io.github.rejeb.dataform.language.gcp.workspace.repository.WorkspaceRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.ListModel;
import javax.swing.ListSelectionModel;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Executor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class DataformCloneDialogComponentTest extends BasePlatformTestCase {

    private static final DataformRepository WITH_REMOTE = new DataformRepository("analytics-core",
            "https://github.com/acme/analytics-core.git", "develop", "dataform@acme.iam.gserviceaccount.com");
    private static final DataformRepository WITHOUT_REMOTE = new DataformRepository("finance-marts", null, null, "");
    private static final Executor DIRECT = Runnable::run;

    private final FakeCredentials credentials = new FakeCredentials();
    private final RecordingStateListener state = new RecordingStateListener();
    private final List<RepositoryCheckoutRequest> started = new ArrayList<>();
    private final List<CheckoutProvider.Listener> startedWith = new ArrayList<>();
    private final CheckoutProvider.Listener checkoutListener = new CheckoutProvider.Listener() {
        @Override
        public void directoryCheckedOut(File directory, VcsKey vcs) {
        }

        @Override
        public void checkoutCompleted() {
        }
    };
    private WorkspaceRepository repository;
    private Path parent;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        repository = mock(WorkspaceRepository.class);
        parent = Files.createTempDirectory("dataform-clone-dialog");
        PropertiesComponent properties = PropertiesComponent.getInstance();
        properties.setValue(DataformCloneDialogComponent.LAST_PROJECT_KEY, "acme");
        properties.setValue(DataformCloneDialogComponent.LAST_LOCATION_KEY, "europe-west1");
        properties.setValue(DataformCloneDialogComponent.LAST_PARENT_KEY, parent.toString());
        when(repository.findRepositories("acme", "europe-west1")).thenReturn(List.of(WITH_REMOTE, WITHOUT_REMOTE));
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            PropertiesComponent properties = PropertiesComponent.getInstance();
            properties.unsetValue(DataformCloneDialogComponent.LAST_PROJECT_KEY);
            properties.unsetValue(DataformCloneDialogComponent.LAST_LOCATION_KEY);
            properties.unsetValue(DataformCloneDialogComponent.LAST_PARENT_KEY);
            NioFiles.deleteRecursively(parent);
        } finally {
            super.tearDown();
        }
    }

    private DataformCloneDialogComponent component(Executor background) {
        DataformCloneDialogComponent component = new DataformCloneDialogComponent(getProject(),
                ModalityState.nonModal(), repository, credentials, (request, listener) -> {
            started.add(request);
            startedWith.add(listener);
        }, background);
        Disposer.register(getTestRootDisposable(), component);
        component.addComponentStateListener(state);
        return component;
    }

    private DataformCloneDialogComponent opened() {
        DataformCloneDialogComponent component = component(DIRECT);
        component.onComponentSelected();
        dispatch();
        return component;
    }

    private static void dispatch() {
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    }

    private static List<String> names(ListModel<DataformRepository> model) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < model.getSize(); i++) {
            names.add(model.getElementAt(i).repositoryId());
        }
        return names;
    }

    private void select(DataformCloneDialogComponent component, DataformRepository value) {
        component.repositoryList().setSelectedValue(value, false);
        dispatch();
    }

    private void workspaces(DataformRepository of, String... ids) {
        List<Workspace> workspaces = new ArrayList<>();
        for (String id : ids) {
            workspaces.add(new Workspace("projects/acme/locations/europe-west1/repositories/" + of.repositoryId()
                    + "/workspaces/" + id, id));
        }
        when(repository.findAll("acme", "europe-west1", of.repositoryId())).thenReturn(workspaces);
    }

    public void testSignedOutTheTabOffersToSignInAndListsNothing() {
        credentials.signedIn = false;

        DataformCloneDialogComponent component = opened();

        assertTrue(component.showsSignIn());
        assertFalse(state.okEnabled);
        verifyNoInteractions(repository);
    }

    public void testTheSignInButtonStartsTheSignIn() {
        credentials.signedIn = false;
        DataformCloneDialogComponent component = opened();

        component.signInButton().doClick();

        assertEquals(List.of(getProject()), credentials.signInProjects);
    }

    public void testFromTheWelcomeScreenTheSignInHasNoProjectToShowProgressIn() {
        credentials.signedIn = false;
        DataformCloneDialogComponent component = new DataformCloneDialogComponent(
                com.intellij.openapi.project.ProjectManager.getInstance().getDefaultProject(),
                ModalityState.nonModal(), repository, credentials, (request, listener) -> {
        }, DIRECT);
        Disposer.register(getTestRootDisposable(), component);
        component.onComponentSelected();
        dispatch();

        component.signInButton().doClick();

        assertEquals(java.util.Collections.singletonList(null), credentials.signInProjects);
    }

    public void testSigningInShowsTheRepositoriesAndTheAccount() {
        credentials.signedIn = false;
        DataformCloneDialogComponent component = opened();
        int itemChanges = state.listItemChanges;

        credentials.signedIn = true;
        publish(AuthStatus.NONE);

        assertFalse(component.showsSignIn());
        assertEquals(List.of("analytics-core", "finance-marts"), names(component.repositoryList().getModel()));
        assertTrue(state.listItemChanges > itemChanges);
    }

    public void testTheRememberedProjectAndLocationAreListedAndNothingIsChosenYet() {
        DataformCloneDialogComponent component = opened();

        assertFalse(component.showsSignIn());
        assertEquals("acme", component.projectField().getText());
        assertEquals("europe-west1", component.locationField().getText());
        assertEquals(List.of("analytics-core", "finance-marts"), names(component.repositoryList().getModel()));
        assertFalse(state.okEnabled);
    }

    public void testWithoutAProjectNothingIsListed() {
        PropertiesComponent.getInstance().unsetValue(DataformCloneDialogComponent.LAST_PROJECT_KEY);

        DataformCloneDialogComponent component = opened();

        assertEquals("", component.projectField().getText());
        assertEquals("Enter a GCP project ID", component.repositoryList().getEmptyText().getText());
        verifyNoInteractions(repository);
    }

    public void testAnEmptyLocationSaysSo() {
        when(repository.findRepositories("acme", "europe-west1")).thenReturn(List.of());

        DataformCloneDialogComponent component = opened();

        assertEquals("No Dataform repository in europe-west1", component.repositoryList().getEmptyText().getText());
    }

    public void testARepositoryWithARemoteDefaultsToCloningIt() {
        DataformCloneDialogComponent component = opened();

        select(component, WITH_REMOTE);

        assertTrue(component.gitRemoteButton().isEnabled());
        assertTrue(component.gitRemoteButton().isSelected());
        assertFalse(component.workspaceCombo().isEnabled());
        assertEquals("Clone", state.okName);
        assertTrue(state.okEnabled);
        verify(repository, never()).findAll(any(), any(), any());
    }

    public void testARepositoryWithoutARemoteOffersItsWorkspaces() {
        workspaces(WITHOUT_REMOTE, "dev-you", "staging");
        DataformCloneDialogComponent component = opened();

        select(component, WITHOUT_REMOTE);

        assertFalse(component.gitRemoteButton().isEnabled());
        assertTrue(component.workspaceButton().isSelected());
        assertTrue(component.workspaceCombo().isEnabled());
        assertEquals("dev-you", component.workspaceCombo().getSelectedItem());
        assertEquals(2, component.workspaceCombo().getItemCount());
        assertEquals("Download", state.okName);
        assertTrue(state.okEnabled);
    }

    public void testChoosingAWorkspaceInsteadOfTheRemoteLoadsTheWorkspaces() {
        workspaces(WITH_REMOTE, "dev-you");
        DataformCloneDialogComponent component = opened();
        select(component, WITH_REMOTE);

        component.workspaceButton().doClick();
        dispatch();

        assertTrue(component.workspaceCombo().isEnabled());
        assertEquals("Download", state.okName);
        assertTrue(state.okEnabled);

        component.gitRemoteButton().doClick();

        assertFalse(component.workspaceCombo().isEnabled());
        assertEquals("Clone", state.okName);
    }

    public void testARepositoryWithoutWorkspacesCannotBeDownloaded() {
        workspaces(WITHOUT_REMOTE);
        DataformCloneDialogComponent component = opened();

        select(component, WITHOUT_REMOTE);

        assertFalse(state.okEnabled);
        assertTrue(component.workspaceStatus().getText(), component.workspaceStatus().getText().contains("no workspace"));
    }

    public void testAFailedWorkspaceListingIsShownAndBlocksTheDownload() {
        when(repository.findAll("acme", "europe-west1", "finance-marts"))
                .thenThrow(new GcpApiException("Permission denied", new RuntimeException()));
        DataformCloneDialogComponent component = opened();

        select(component, WITHOUT_REMOTE);

        assertFalse(state.okEnabled);
        assertEquals("Permission denied", component.workspaceStatus().getText());
    }

    public void testTheDirectoryFollowsTheRepositoryUntilItIsEdited() {
        workspaces(WITHOUT_REMOTE, "dev-you");
        DataformCloneDialogComponent component = opened();

        select(component, WITH_REMOTE);
        assertEquals(parent.resolve("analytics-core").toString(), component.directoryField().getText());

        select(component, WITHOUT_REMOTE);
        assertEquals(parent.resolve("finance-marts").toString(), component.directoryField().getText());

        component.directoryField().setText(parent.resolve("mine").toString());
        select(component, WITH_REMOTE);
        assertEquals(parent.resolve("mine").toString(), component.directoryField().getText());
    }

    public void testCloneAsksForTheRemoteOfTheChosenRepository() {
        DataformCloneDialogComponent component = opened();
        select(component, WITH_REMOTE);

        component.doClone(checkoutListener);

        assertEquals(List.of(new RepositoryCheckoutRequest("acme", "europe-west1", WITH_REMOTE,
                RepositorySource.GIT_REMOTE, null, parent.resolve("analytics-core"))), started);
        assertSame(checkoutListener, startedWith.getFirst());
    }

    public void testDownloadAsksForTheChosenWorkspace() {
        workspaces(WITHOUT_REMOTE, "dev-you", "staging");
        DataformCloneDialogComponent component = opened();
        select(component, WITHOUT_REMOTE);

        component.workspaceCombo().setSelectedItem("staging");
        component.doClone(checkoutListener);

        assertEquals(List.of(new RepositoryCheckoutRequest("acme", "europe-west1", WITHOUT_REMOTE,
                RepositorySource.WORKSPACE, "staging", parent.resolve("finance-marts"))), started);
    }

    public void testTheNextOpeningStartsFromTheLastChoices() {
        when(repository.findRepositories("other", "us-central1")).thenReturn(List.of(WITH_REMOTE));
        DataformCloneDialogComponent component = opened();
        component.projectField().setText("other");
        component.locationField().setText("us-central1");
        component.reloadRepositories();
        dispatch();
        select(component, WITH_REMOTE);

        component.doClone(checkoutListener);

        PropertiesComponent properties = PropertiesComponent.getInstance();
        assertEquals("other", properties.getValue(DataformCloneDialogComponent.LAST_PROJECT_KEY));
        assertEquals("us-central1", properties.getValue(DataformCloneDialogComponent.LAST_LOCATION_KEY));
        assertEquals(parent.toString(), properties.getValue(DataformCloneDialogComponent.LAST_PARENT_KEY));
    }

    public void testAProjectEditedAfterTheListingDoesNotChangeWhereTheRepositoryComesFrom() {
        DataformCloneDialogComponent component = opened();
        select(component, WITH_REMOTE);

        component.projectField().setText("half-typed");
        component.doClone(checkoutListener);

        assertEquals("acme", started.getFirst().projectId());
    }

    public void testAnEmptyOrMissingDirectoryIsAccepted() throws IOException {
        DataformCloneDialogComponent component = opened();
        select(component, WITH_REMOTE);
        assertEmpty(component.doValidateAll());

        Files.createDirectories(parent.resolve("analytics-core"));
        assertEmpty(component.doValidateAll());
    }

    public void testADirectoryWithFilesIsRefused() throws IOException {
        DataformCloneDialogComponent component = opened();
        select(component, WITH_REMOTE);
        Files.createDirectories(parent.resolve("analytics-core"));
        Files.writeString(parent.resolve("analytics-core/existing.txt"), "");

        List<ValidationInfo> problems = component.doValidateAll();

        assertEquals(1, problems.size());
        assertTrue(problems.getFirst().message, problems.getFirst().message.contains("not empty"));
    }

    public void testAFileInPlaceOfTheDirectoryIsRefused() throws IOException {
        DataformCloneDialogComponent component = opened();
        select(component, WITH_REMOTE);
        Files.writeString(parent.resolve("analytics-core"), "");

        assertEquals(1, component.doValidateAll().size());
    }

    public void testNoDirectoryIsRefused() {
        DataformCloneDialogComponent component = opened();
        select(component, WITH_REMOTE);
        component.directoryField().setText(" ");

        assertEquals(1, component.doValidateAll().size());
    }

    public void testSearchingFiltersTheRepositoriesAndKeepsTheChoiceWhenItStillMatches() {
        workspaces(WITHOUT_REMOTE, "dev-you");
        DataformCloneDialogComponent component = opened();
        select(component, WITHOUT_REMOTE);

        component.searchField().setText("FIN");
        dispatch();

        assertEquals(List.of("finance-marts"), names(component.repositoryList().getModel()));
        assertEquals(WITHOUT_REMOTE, component.repositoryList().getSelectedValue());
        verify(repository, org.mockito.Mockito.times(1)).findAll("acme", "europe-west1", "finance-marts");
    }

    public void testSearchingAwayTheChoiceClearsIt() {
        DataformCloneDialogComponent component = opened();
        select(component, WITH_REMOTE);

        component.searchField().setText("fin");
        dispatch();

        assertFalse(state.okEnabled);
    }

    public void testAFailedListingIsShownAndCleanedByTheNextOne() {
        when(repository.findRepositories("acme", "europe-west1"))
                .thenThrow(new GcpApiException("Permission denied on acme", new RuntimeException()))
                .thenReturn(List.of(WITH_REMOTE));
        DataformCloneDialogComponent component = opened();

        assertEquals("Permission denied on acme", component.listingProblem());
        assertEmpty(names(component.repositoryList().getModel()));

        component.reloadRepositories();
        dispatch();

        assertNull(component.listingProblem());
        assertEquals(List.of("analytics-core"), names(component.repositoryList().getModel()));
    }

    public void testACredentialThatExpiredGoesBackToTheSignIn() {
        when(repository.findRepositories("acme", "europe-west1"))
                .thenThrow(credentialRejected());

        DataformCloneDialogComponent component = opened();

        assertTrue(component.showsSignIn());
        assertNull(component.listingProblem());
    }

    public void testAnAnswerToAnOlderListingIsIgnored() {
        when(repository.findRepositories("newer", "europe-west1")).thenReturn(List.of(WITHOUT_REMOTE));
        Deque<Runnable> pending = new ArrayDeque<>();
        DataformCloneDialogComponent component = component(pending::add);
        component.onComponentSelected();
        pending.removeFirst().run();
        dispatch();
        Runnable older = pending.removeFirst();

        component.projectField().setText("newer");
        component.reloadRepositories();
        pending.removeFirst().run();
        older.run();
        dispatch();

        assertEquals(List.of("finance-marts"), names(component.repositoryList().getModel()));
    }

    public void testAnAnswerToAnOlderWorkspaceListingIsIgnored() {
        workspaces(WITH_REMOTE, "of-the-first");
        workspaces(WITHOUT_REMOTE, "of-the-second");
        Deque<Runnable> pending = new ArrayDeque<>();
        DataformCloneDialogComponent component = component(pending::add);
        component.onComponentSelected();
        pending.removeFirst().run();
        dispatch();
        pending.removeFirst().run();
        dispatch();
        select(component, WITH_REMOTE);
        component.workspaceButton().doClick();
        Runnable older = pending.removeFirst();

        select(component, WITHOUT_REMOTE);
        pending.removeFirst().run();
        older.run();
        dispatch();

        assertEquals("of-the-second", component.workspaceCombo().getSelectedItem());
        assertEquals(1, component.workspaceCombo().getItemCount());
    }

    public void testTypingAProjectListsItsRepositoriesAfterAPause() {
        when(repository.findRepositories("typed", "europe-west1")).thenReturn(List.of(WITHOUT_REMOTE));
        DataformCloneDialogComponent component = opened();

        component.projectField().setText("typed");

        PlatformTestUtil.waitWithEventsDispatching("the repositories of the typed project were not listed",
                () -> names(component.repositoryList().getModel()).equals(List.of("finance-marts")), 5);
    }

    public void testPickingAnotherLocationListsItsRepositories() {
        when(repository.findRepositories("acme", "us-central1")).thenReturn(List.of(WITHOUT_REMOTE));
        DataformCloneDialogComponent component = opened();

        component.locationField().setText("us-central1");

        PlatformTestUtil.waitWithEventsDispatching("the repositories of the location were not listed",
                () -> names(component.repositoryList().getModel()).equals(List.of("finance-marts")), 5);
    }

    public void testAnAnswerWhileAnotherTabIsShownLeavesItsOkButtonAlone() {
        workspaces(WITHOUT_REMOTE, "dev-you");
        Deque<Runnable> pending = new ArrayDeque<>();
        DataformCloneDialogComponent component = component(pending::add);
        component.onComponentSelected();
        pending.removeFirst().run();
        dispatch();
        pending.removeFirst().run();
        dispatch();
        select(component, WITHOUT_REMOTE);
        component.getView().setVisible(false);

        pending.removeFirst().run();
        dispatch();

        assertFalse(state.okEnabled);

        component.onComponentSelected();

        assertTrue(state.okEnabled);
        assertEquals("Download", state.okName);
        assertTrue(pending.isEmpty());
    }

    public void testAnAnswerAfterTheDialogClosedIsDropped() {
        Deque<Runnable> pending = new ArrayDeque<>();
        DataformCloneDialogComponent component = component(pending::add);
        component.onComponentSelected();
        pending.removeFirst().run();
        dispatch();
        Runnable listing = pending.removeFirst();

        Disposer.dispose(component);
        listing.run();
        dispatch();

        assertEmpty(names(component.repositoryList().getModel()));
    }

    public void testTheChoiceIsFetchedAfterTheDialogClosed() {
        DataformCloneDialogComponent component = opened();
        select(component, WITH_REMOTE);

        Disposer.dispose(component);
        component.doClone(checkoutListener);

        assertEquals(1, started.size());
    }

    public void testTheSameRepositoryInAnotherProjectIsChosenAnew() {
        workspaces(WITHOUT_REMOTE, "dev-you");
        when(repository.findRepositories("other", "europe-west1")).thenReturn(List.of(WITH_REMOTE, WITHOUT_REMOTE));
        DataformCloneDialogComponent component = opened();
        select(component, WITHOUT_REMOTE);

        component.projectField().setText("other");
        component.reloadRepositories();
        dispatch();

        assertNull(component.repositoryList().getSelectedValue());
        assertEquals(0, component.workspaceCombo().getItemCount());
        assertFalse(state.okEnabled);
    }

    public void testAFailedListingDropsTheChoice() {
        when(repository.findRepositories("acme", "europe-west1"))
                .thenReturn(List.of(WITH_REMOTE))
                .thenThrow(new GcpApiException("Permission denied on acme", new RuntimeException()));
        DataformCloneDialogComponent component = opened();
        select(component, WITH_REMOTE);

        component.reloadRepositories();
        dispatch();

        assertNull(component.repositoryList().getSelectedValue());
        assertFalse(state.okEnabled);
    }

    public void testASignInThatBecameNecessaryLeavesTheRepositories() {
        DataformCloneDialogComponent component = opened();
        select(component, WITH_REMOTE);

        publish(AuthStatus.REQUIRED);

        assertTrue(component.showsSignIn());
        assertFalse(state.okEnabled);
    }

    public void testASignInInProgressDisablesTheButtonUntilItEnds() {
        credentials.signedIn = false;
        DataformCloneDialogComponent component = opened();

        publish(AuthStatus.IN_PROGRESS);
        assertFalse(component.signInButton().isEnabled());

        publish(AuthStatus.NONE);
        assertTrue(component.signInButton().isEnabled());
        assertTrue(component.showsSignIn());
    }

    public void testNothingHappensBeforeTheTabIsSelected() {
        component(DIRECT);

        publish(AuthStatus.NONE);

        verifyNoInteractions(repository);
    }

    public void testACredentialThatExpiredWhileListingWorkspacesGoesBackToTheSignIn() {
        when(repository.findAll("acme", "europe-west1", "finance-marts")).thenThrow(credentialRejected());
        DataformCloneDialogComponent component = opened();

        select(component, WITHOUT_REMOTE);

        assertTrue(component.showsSignIn());
        assertFalse(state.okEnabled);
    }

    public void testOnlyOneRepositoryCanBeChosen() {
        assertEquals(ListSelectionModel.SINGLE_SELECTION, opened().repositoryList().getSelectionMode());
    }

    public void testWithoutALocationNothingIsListed() {
        DataformCloneDialogComponent component = opened();

        component.locationField().setText("");
        component.reloadRepositories();
        dispatch();

        assertEquals("Enter a location", component.repositoryList().getEmptyText().getText());
        verify(repository, never()).findRepositories("acme", "");
    }

    public void testWhileListingTheEmptyListSaysSo() {
        Deque<Runnable> pending = new ArrayDeque<>();
        DataformCloneDialogComponent component = component(pending::add);
        component.onComponentSelected();
        pending.removeFirst().run();
        dispatch();

        assertEquals("Loading repositories…", component.repositoryList().getEmptyText().getText());
    }

    public void testTheFieldsAreNamedForScreenReaders() {
        DataformCloneDialogComponent component = opened();

        assertEquals("GCP project:", component.projectField().getAccessibleContext().getAccessibleName());
        assertEquals("Dataform repositories", component.repositoryList().getAccessibleContext().getAccessibleName());
        assertEquals("Workspace", component.workspaceCombo().getAccessibleContext().getAccessibleName());
        assertEquals("Search repositories",
                component.searchField().getTextEditor().getAccessibleContext().getAccessibleName());
    }

    private static void publish(AuthStatus status) {
        ApplicationManager.getApplication().getMessageBus().syncPublisher(DataformAuthListener.TOPIC)
                .authStatusChanged(status);
        dispatch();
    }

    private static GcpApiException credentialRejected() {
        GcpApiException failure = new GcpApiException("rejected");
        failure.initCause(new GcpAuthRequiredException("sign in"));
        return failure;
    }

    private static final class RecordingStateListener implements VcsCloneDialogComponentStateListener {
        private String okName;
        private boolean okEnabled;
        private int listItemChanges;

        @Override
        public void onOkActionNameChanged(@NotNull String name) {
            okName = name;
        }

        @Override
        public void onOkActionEnabled(boolean enabled) {
            okEnabled = enabled;
        }

        @Override
        public void onListItemChanged() {
            listItemChanges++;
        }
    }

    private static final class FakeCredentials implements DataformCredentialsService {
        private boolean signedIn = true;
        private final List<Project> signInProjects = new ArrayList<>();

        @Override
        public @NotNull GoogleCredentials get() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void invalidate() {
        }

        @Override
        public boolean isSignedIn() {
            return signedIn;
        }

        @Override
        public boolean isSignedInCached() {
            return signedIn;
        }

        @Override
        public @Nullable String getAccountEmail() {
            return signedIn ? "you@example.com" : null;
        }

        @Override
        public void signIn(@Nullable Project project) {
            signInProjects.add(project);
        }

        @Override
        public void signOut() {
            signedIn = false;
        }
    }
}
