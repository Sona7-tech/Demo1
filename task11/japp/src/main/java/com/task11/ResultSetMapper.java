package com.task11;
import java.sql.ResultSet;
import java.util.*;

public class ResultSetMapper {

    // ---------------- SHIPMENT ----------------
    public static Map<String, Object> shipment(ResultSet rs) throws Exception {
        Map<String, Object> m = new HashMap<>();

        m.put("shipment_id", rs.getString("shipment_id"));
        m.put("order_id", rs.getString("order_id"));
        m.put("origin", rs.getString("origin"));
        m.put("destination", rs.getString("destination"));
        m.put("weight_kg", rs.getBigDecimal("weight_kg"));
        m.put("created_at", rs.getTimestamp("created_at"));

        return m;
    }

    // ---------------- CARRIER ----------------
    public static Map<String, Object> carrier(ResultSet rs) throws Exception {
        Map<String, Object> m = new HashMap<>();

        m.put("carrier_id", rs.getString("carrier_id"));
        m.put("name", rs.getString("name"));
        m.put("email", rs.getString("email"));
        m.put("phone", rs.getString("phone"));
        m.put("is_active", rs.getBoolean("is_active"));

        return m;
    }

    // ---------------- STATUS LIST ----------------
    public static List<Map<String, Object>> statusList(ResultSet rs) throws Exception {

        List<Map<String, Object>> list = new ArrayList<>();

        while (rs.next()) {
            Map<String, Object> m = new HashMap<>();

            m.put("update_id", rs.getInt("update_id"));
            m.put("shipment_id", rs.getString("shipment_id"));
            m.put("carrier_id", rs.getString("carrier_id"));
            m.put("status", rs.getString("status"));
            m.put("location", rs.getString("location"));
            m.put("notes", rs.getString("notes"));
            m.put("timestamp", rs.getTimestamp("timestamp"));

            list.add(m);
        }

        return list;
    }
}