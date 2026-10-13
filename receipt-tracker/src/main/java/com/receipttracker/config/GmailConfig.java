package com.receipttracker.config;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.gmail.Gmail;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.List;

@Configuration
@Slf4j
public class GmailConfig {

    @Value("${gmail.credentials-file}")
    private String credentialsFile;

    @Value("${gmail.tokens-directory}")
    private String tokensDirectory;

    @Value("${gmail.application-name}")
    private String applicationName;

    // Scope corrigé — chaîne littérale sans caractère parasite
    private static final List<String> SCOPES =
            List.of("https://www.googleapis.com/auth/gmail.readonly");

    private static final GsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    @Bean(name = "gmailClient")
    @Profile("!test")
    public Gmail gmailClient() throws GeneralSecurityException, IOException {
        final NetHttpTransport transport = GoogleNetHttpTransport.newTrustedTransport();
        Credential credential = authorize(transport);
        return new Gmail.Builder(transport, JSON_FACTORY, credential)
                .setApplicationName(applicationName)
                .build();
    }

    private Credential authorize(NetHttpTransport transport) throws IOException {
        InputStream credentialsStream = loadCredentialsStream();
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY,
                new InputStreamReader(credentialsStream));

        Path tokensPath = Path.of(tokensDirectory);
        Files.createDirectories(tokensPath);

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                transport, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(tokensPath.toFile()))
                .setAccessType("offline")
                .build();

        LocalServerReceiver receiver = new LocalServerReceiver.Builder()
                .setPort(8888)
                .build();

        Credential credential = new AuthorizationCodeInstalledApp(flow, receiver)
                .authorize("user");

        log.info("Gmail OAuth2 autorisé avec succès");
        return credential;
    }

    private InputStream loadCredentialsStream() throws FileNotFoundException {
        InputStream stream = getClass().getClassLoader().getResourceAsStream(credentialsFile);
        if (stream != null) return stream;

        File file = new File(credentialsFile);
        if (file.exists()) return new FileInputStream(file);

        throw new FileNotFoundException(
            "Fichier credentials.json introuvable : " + credentialsFile +
            "\nTéléchargez-le depuis https://console.cloud.google.com/apis/credentials"
        );
    }
}
