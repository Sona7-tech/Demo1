package com.task11;
import java.io.BufferedReader;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.OffsetDateTime;

public class ETLService {

    // ---------------- SHIPMENTS ----------------
    public static void loadShipments(BufferedReader reader, Connection conn) throws Exception {

        String sql =
                "INSERT INTO shipments (" +
                        "shipment_id, order_id, origin, destination, weight_kg, created_at" +
                        ") VALUES (?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {

            reader.readLine();

            String line;
            while ((line = reader.readLine()) != null) {

                String[] p = line.split(",");

                ps.setString(1, p[0]);
                ps.setString(2, p[1]);
                ps.setString(3, p[2]);
                ps.setString(4, p[3]);
                ps.setBigDecimal(5, new BigDecimal(p[4]));
                ps.setTimestamp(6,
                        Timestamp.from(OffsetDateTime.parse(p[5]).toInstant())
                );

                ps.addBatch();
            }

            ps.executeBatch();
        }
    }

    // ---------------- CARRIERS ----------------
    public static void loadCarriers(BufferedReader reader, Connection conn) throws Exception {

        String sql =
                "INSERT INTO carriers (" +
                        "carrier_id, name, email, phone, is_active" +
                        ") VALUES (?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {

            reader.readLine();

            String line;
            while ((line = reader.readLine()) != null) {

                String[] p = line.split(",");

                ps.setString(1, p[0]);
                ps.setString(2, p[1]);
                ps.setString(3, p[2]);
                ps.setString(4, p[3]);
                ps.setBoolean(5, Boolean.parseBoolean(p[4]));

                ps.addBatch();
            }

            ps.executeBatch();
        }
    }

    // ---------------- STATUS UPDATES ----------------
    public static void loadStatusUpdates(BufferedReader reader, Connection conn) throws Exception {

        String sql =
                "INSERT INTO status_updates (" +
                        "shipment_id, carrier_id, status, location, notes, timestamp" +
                        ") VALUES (?, ?, CAST(? AS StatusType), ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {

            reader.readLine();

            String line;
            while ((line = reader.readLine()) != null) {

                String[] p = line.split(",");

                ps.setString(1, p[0]);
                ps.setString(2, p[1]);
                ps.setString(3, p[2]);
                ps.setString(4, p[3]);
                ps.setString(5, p[4]);
                ps.setTimestamp(6,
                        Timestamp.from(OffsetDateTime.parse(p[5]).toInstant())
                );

                ps.addBatch();
            }

            ps.executeBatch();
        }
    }
}