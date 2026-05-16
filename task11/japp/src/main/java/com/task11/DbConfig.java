package com.task11;


import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;



public final class DbConfig {

    private static final String DB_ENDPOINT = System.getenv("DB_ENDPOINT");
    private static final String DB_NAME = "logisticdb";
    private static final String USER = System.getenv("DB_USER");
    private static final String PASSWORD = System.getenv("DB_PASSWORD");

    public static Connection getConnection() throws SQLException {

        if (DB_ENDPOINT == null || USER == null || PASSWORD == null) {
            throw new RuntimeException("DB env variables missing!");
        }

        String url =
                "jdbc:postgresql://" + DB_ENDPOINT + ":5432/" + DB_NAME;

        return DriverManager.getConnection(url, USER, PASSWORD);
    }
}

