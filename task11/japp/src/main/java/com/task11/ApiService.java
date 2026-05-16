package com.task11;
import java.sql.*;
import java.util.Map;

import java.sql.*;
import java.sql.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.*;
import java.math.BigDecimal;

public class ApiService {

    private static final ObjectMapper mapper = new ObjectMapper();

    // ---------------- INIT DB ----------------
    public void initDb() throws Exception {

        try (Connection c = DbConfig.getConnection();
             Statement st = c.createStatement()) {

            try {
                st.execute(
                        "CREATE TYPE StatusType AS ENUM " +
                                "('CREATED','IN_TRANSIT','DELAYED','DELIVERED','CANCELLED')"
                );
            } catch (Exception ignored) {}

            st.execute(
                    "CREATE TABLE IF NOT EXISTS shipments (" +
                            "shipment_id VARCHAR(50) PRIMARY KEY," +
                            "order_id VARCHAR(50)," +
                            "origin VARCHAR(100)," +
                            "destination VARCHAR(100)," +
                            "weight_kg DECIMAL(10,2)," +
                            "created_at TIMESTAMP" +
                            ")"
            );

            st.execute(
                    "CREATE TABLE IF NOT EXISTS carriers (" +
                            "carrier_id VARCHAR(50) PRIMARY KEY," +
                            "name VARCHAR(100)," +
                            "email VARCHAR(100)," +
                            "phone VARCHAR(20)," +
                            "is_active BOOLEAN" +
                            ")"
            );

            st.execute(
                    "CREATE TABLE IF NOT EXISTS status_updates (" +
                            "update_id SERIAL PRIMARY KEY," +
                            "shipment_id VARCHAR(50)," +
                            "carrier_id VARCHAR(50)," +
                            "status StatusType," +
                            "location VARCHAR(100)," +
                            "notes TEXT," +
                            "timestamp TIMESTAMP" +
                            ")"
            );
        }
    }

    // =========================================================
    // ---------------- SHIPMENTS ----------------
    // =========================================================

    public String createShipment(String body) throws Exception {

        Map<String, String> r = mapper.readValue(body, Map.class);

        try (Connection c = DbConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO shipments " +
                             "(shipment_id, order_id, origin, destination, weight_kg, created_at) " +
                             "VALUES (?, ?, ?, ?, ?, now())"
             )) {

            ps.setString(1, r.get("shipment_id"));
            ps.setString(2, r.get("order_id"));
            ps.setString(3, r.get("origin"));
            ps.setString(4, r.get("destination"));
            ps.setBigDecimal(5, new BigDecimal(r.get("weight_kg")));

            ps.executeUpdate();
        }

        return "CREATED";
    }

    public String getShipment(String id) throws Exception {

        try (Connection c = DbConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM shipments WHERE shipment_id=?"
             )) {

            ps.setString(1, id);

            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return mapper.writeValueAsString(ResultSetMapper.shipment(rs));
            }

            return "NOT_FOUND";
        }
    }

    public String updateShipment(String id, String body) throws Exception {

        Map<String, String> r = mapper.readValue(body, Map.class);

        try (Connection c = DbConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE shipments SET order_id=?, origin=?, destination=?, weight_kg=? " +
                             "WHERE shipment_id=?"
             )) {

            ps.setString(1, r.get("order_id"));
            ps.setString(2, r.get("origin"));
            ps.setString(3, r.get("destination"));
            ps.setBigDecimal(4, new BigDecimal(r.get("weight_kg")));
            ps.setString(5, id);

            ps.executeUpdate();
        }

        return "UPDATED";
    }

    public void deleteShipment(String id) throws Exception {

        try (Connection c = DbConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM shipments WHERE shipment_id=?"
             )) {

            ps.setString(1, id);
            ps.executeUpdate();
        }
    }

    // =========================================================
    // ---------------- CARRIERS ----------------
    // =========================================================

    public String createCarrier(String body) throws Exception {

        Map<String, String> r = mapper.readValue(body, Map.class);

        try (Connection c = DbConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO carriers (carrier_id, name, email, phone, is_active) " +
                             "VALUES (?, ?, ?, ?, ?)"
             )) {

            ps.setString(1, r.get("carrier_id"));
            ps.setString(2, r.get("name"));
            ps.setString(3, r.get("email"));
            ps.setString(4, r.get("phone"));
            ps.setBoolean(5, Boolean.parseBoolean(r.get("is_active")));

            ps.executeUpdate();
        }

        return "CREATED";
    }

    public String getCarrier(String id) throws Exception {

        try (Connection c = DbConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM carriers WHERE carrier_id=?"
             )) {

            ps.setString(1, id);

            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return mapper.writeValueAsString(ResultSetMapper.carrier(rs));
            }

            return "NOT_FOUND";
        }
    }

    public String updateCarrier(String id, String body) throws Exception {

        Map<String, String> r = mapper.readValue(body, Map.class);

        try (Connection c = DbConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE carriers SET name=?, email=?, phone=?, is_active=? " +
                             "WHERE carrier_id=?"
             )) {

            ps.setString(1, r.get("name"));
            ps.setString(2, r.get("email"));
            ps.setString(3, r.get("phone"));
            ps.setBoolean(4, Boolean.parseBoolean(r.get("is_active")));
            ps.setString(5, id);

            ps.executeUpdate();
        }

        return "UPDATED";
    }

    public void deleteCarrier(String id) throws Exception {

        try (Connection c = DbConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM carriers WHERE carrier_id=?"
             )) {

            ps.setString(1, id);
            ps.executeUpdate();
        }
    }

    // =========================================================
    // ---------------- STATUS ----------------
    // =========================================================

    public String createStatus(String body) throws Exception {

        Map<String, String> r = mapper.readValue(body, Map.class);

        try (Connection c = DbConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO status_updates " +
                             "(shipment_id, carrier_id, status, location, notes, timestamp) " +
                             "VALUES (?, ?, CAST(? AS StatusType), ?, ?, now())"
             )) {

            ps.setString(1, r.get("shipment_id"));
            ps.setString(2, r.get("carrier_id"));
            ps.setString(3, r.get("status"));
            ps.setString(4, r.get("location"));
            ps.setString(5, r.get("notes"));

            ps.executeUpdate();
        }

        return "CREATED";
    }

    public String getStatus(String shipmentId) throws Exception {

        try (Connection c = DbConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM status_updates WHERE shipment_id=?"
             )) {

            ps.setString(1, shipmentId);

            ResultSet rs = ps.executeQuery();

            return mapper.writeValueAsString(ResultSetMapper.statusList(rs));
        }
    }
}