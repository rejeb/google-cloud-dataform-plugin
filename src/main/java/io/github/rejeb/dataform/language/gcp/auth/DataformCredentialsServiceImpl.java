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
package io.github.rejeb.dataform.language.gcp.auth;

import com.google.auth.oauth2.GoogleAuthUtils;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.UserCredentials;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DataformCredentialsServiceImpl implements DataformCredentialsService {

    private static final Logger LOG = Logger.getInstance(DataformCredentialsServiceImpl.class);

    private static final String SIGNED_OUT_KEY = "dataform.gcp.auth.signedOut";

    private final DataformCredentialsStore store = new PasswordSafeCredentialsStore();
    private final AtomicBoolean signInRunning = new AtomicBoolean(false);

    private volatile GoogleCredentials cached;
    private volatile String accountEmail;

    @Override
    public @NotNull GoogleCredentials get() {
        GoogleCredentials current = cached;
        if (current != null && tryRefresh(current)) {
            return current;
        }
        synchronized (this) {
            current = cached;
            if (current != null && tryRefresh(current)) {
                return current;
            }
            GoogleCredentials resolved = fromStore();
            if (resolved == null) {
                resolved = fromApplicationDefault();
            }
            if (resolved == null) {
                throw new GcpAuthRequiredException(
                        "No usable Google credential. Sign in from the Dataform banner.");
            }
            cached = resolved;
            return resolved;
        }
    }

    @Override
    public void invalidate() {
        cached = null;
    }

    @Override
    public boolean isSignedIn() {
        try {
            get();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public @Nullable String getAccountEmail() {
        if (accountEmail == null) {
            store.load().ifPresent(stored -> accountEmail = stored.accountEmail());
        }
        return accountEmail;
    }

    @Override
    public void signIn(@Nullable Project project) {
        if (!OAuthClientConfig.isConfigured()) {
            DataformAuthState.getInstance().markSignInFailed(
                    "no OAuth client is configured for this plugin build.");
            return;
        }
        if (!signInRunning.compareAndSet(false, true)) {
            return;
        }
        DataformAuthState.getInstance().markSignInStarted();
        new Task.Backgroundable(project, "Signing in to Google Cloud", true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                runSignIn(indicator);
            }
        }.queue();
    }

    @Override
    public void signOut() {
        synchronized (this) {
            cached = null;
            accountEmail = null;
            store.clear();
            setSignedOut(true);
        }
        DataformAuthState.getInstance().markAuthRequired(AuthTrigger.USER_ACTION);
    }

    private void runSignIn(@NotNull ProgressIndicator indicator) {
        try {
            StoredCredentials credentials = GoogleOAuthLoginFlow.execute(indicator);
            synchronized (this) {
                store.save(credentials);
                accountEmail = credentials.accountEmail();
                cached = null;
                setSignedOut(false);
            }
            get();
            DataformAuthState.getInstance().markAuthenticated();
        } catch (ProcessCanceledException e) {
            DataformAuthState.getInstance().markSignInFailed("Sign-in cancelled.");
        } catch (Exception e) {
            LOG.warn("Google sign-in failed.", e);
            DataformAuthState.getInstance().markSignInFailed(e.getMessage());
        } finally {
            signInRunning.set(false);
        }
    }

    @Nullable
    private GoogleCredentials fromStore() {
        Optional<StoredCredentials> stored = store.load();
        if (stored.isEmpty()) {
            return null;
        }
        StoredCredentials value = stored.get();
        accountEmail = value.accountEmail();
        GoogleCredentials credentials = UserCredentials.newBuilder()
                .setClientId(value.clientId())
                .setClientSecret(value.clientSecret())
                .setRefreshToken(value.refreshToken())
                .setHttpTransportFactory(SslConfig.httpTransportFactory())
                .build()
                .createScoped(OAuthClientConfig.SCOPES);
        return tryRefresh(credentials) ? credentials : null;
    }

    @Nullable
    private GoogleCredentials fromApplicationDefault() {
        if (isSignedOut()) {
            return null;
        }
        String envPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
        String adcPath = envPath != null ? envPath : GoogleAuthUtils.getWellKnownCredentialsPath();
        Path path = Path.of(adcPath);
        if (!Files.exists(path)) {
            return null;
        }
        try (InputStream stream = Files.newInputStream(path)) {
            GoogleCredentials credentials = GoogleCredentials
                    .fromStream(stream, SslConfig.httpTransportFactory())
                    .createScoped();
            return tryRefresh(credentials) ? credentials : null;
        } catch (IOException e) {
            LOG.debug("Application default credentials are not usable.", e);
            return null;
        }
    }

    private static boolean isSignedOut() {
        return PropertiesComponent.getInstance().getBoolean(SIGNED_OUT_KEY, false);
    }

    private static void setSignedOut(boolean signedOut) {
        PropertiesComponent.getInstance().setValue(SIGNED_OUT_KEY, signedOut, false);
    }

    private boolean tryRefresh(@NotNull GoogleCredentials credentials) {
        assertBackgroundThread();
        try {
            credentials.refreshIfExpired();
            return credentials.getAccessToken() != null;
        } catch (IOException e) {
            LOG.debug("Google credential refresh failed.", e);
            return false;
        }
    }

    private static void assertBackgroundThread() {
        if (ApplicationManager.getApplication().isDispatchThread()) {
            LOG.error("Google credentials must not be resolved on the EDT.");
        }
    }
}
