package com.task11;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public final class DbConfig {

    private static final String DATABASE_NAME = "logisticdb";

    private static volatile String cachedUsername;
    private static volatile String cachedPassword;

    private DbConfig() {
    }

    public static Connection getConnection() throws SQLException {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("PostgreSQL JDBC driver not found", e);
        }

        loadCredentialsIfNeeded();

        String endpoint = System.getenv("DB_ENDPOINT");
        String url = "jdbc:postgresql://" + endpoint + ":5432/" + DATABASE_NAME;

        Properties props = new Properties();
        props.setProperty("user", cachedUsername);
        props.setProperty("password", cachedPassword);
        props.setProperty("ssl", "false");

        return DriverManager.getConnection(url, props);
    }

    private static void loadCredentialsIfNeeded() {
        if (cachedUsername != null && cachedPassword != null) {
            return;
        }
        synchronized (DbConfig.class) {
            if (cachedUsername != null && cachedPassword != null) {
                return;
            }
            String secretName = System.getenv("MASTER_USER_SECRET_NAME");
            String region = System.getenv().getOrDefault("REGION",
                    System.getenv().getOrDefault("AWS_REGION", "eu-west-1"));

            try (SecretsManagerClient client = SecretsManagerClient.builder()
                    .region(Region.of(region))
                    .build()) {

                GetSecretValueResponse response = client.getSecretValue(
                        GetSecretValueRequest.builder().secretId(secretName).build());

                JsonNode secret = new ObjectMapper().readTree(response.secretString());
                cachedUsername = secret.get("username").asText();
                cachedPassword = secret.get("password").asText();
            } catch (Exception e) {
                throw new RuntimeException("Failed to load DB credentials from Secrets Manager", e);
            }
        }
    }
}
