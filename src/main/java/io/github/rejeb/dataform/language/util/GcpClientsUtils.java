package io.github.rejeb.dataform.language.util;

import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.dataform.v1.DataformClient;
import com.google.cloud.dataform.v1.DataformSettings;
import com.intellij.openapi.diagnostic.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class GcpClientsUtils {
    private static final Logger LOG = Logger.getInstance(GcpClientsUtils.class);

    private static GoogleCredentials googleCredentials;

    public static BigQuery bigQuery(String projectId) {
        return BigQueryOptions.newBuilder()
                .setCredentials(GcpClientsUtils.getCredentials())
                .setProjectId(projectId)
                .build()
                .getService();
    }

    public static DataformClient dataformClient() throws IOException {
        DataformSettings settings = DataformSettings.newBuilder()
                .setCredentialsProvider(GcpClientsUtils::getCredentials)
                .build();
        return DataformClient.create(settings);
    }

    public static synchronized Credentials getCredentials() throws RuntimeException {
        if (googleCredentials != null) {
            try {
                googleCredentials.refreshIfExpired();
                return googleCredentials;
            } catch (IOException e) {
                LOG.warn("Cached credentials refresh failed, reloading from ADC.", e);
            }
        }
        googleCredentials = loadFromDisk();
        return googleCredentials;
    }

    private static GoogleCredentials loadFromDisk() throws RuntimeException {
        Path adcPath = Path.of(System.getProperty("user.home"),
                ".config", "gcloud", "application_default_credentials.json");
        String envPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
        if (envPath != null) {
            adcPath = Path.of(envPath);
        }
        try (InputStream stream = Files.newInputStream(adcPath)) {
            return GoogleCredentials.fromStream(stream).createScoped();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
