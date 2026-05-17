package com.task11;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.environment.EnvironmentVariables;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.model.RetentionSetting;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.syndicate.deployment.model.environment.ValueTransformer.RDS_DB_CLUSTER_NAME_TO_ENDPOINT;
import static com.syndicate.deployment.model.environment.ValueTransformer.RDS_DB_CLUSTER_NAME_TO_MASTER_USER_SECRET_NAME;

@LambdaHandler(
    lambdaName = "api_handler",
	roleName = "api_handler-role",
	isPublishVersion = true,
	aliasName = "${lambdas_alias_name}",
	logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED,
	memory = 300,
	timeout = 300,
	subnetsIds = {"${lambda_sn_id}"},
	securityGroupIds = {"${logistic_sg_id}"}
)

@EnvironmentVariables(value = {
		@EnvironmentVariable(key = "REGION", value = "${region}"),
		@EnvironmentVariable(
				key = "DB_ENDPOINT",
				value = "logistic-cluster",
				valueTransformer = RDS_DB_CLUSTER_NAME_TO_ENDPOINT
		),
		@EnvironmentVariable(
				key = "MASTER_USER_SECRET_NAME",
				value = "logistic-cluster",
				valueTransformer = RDS_DB_CLUSTER_NAME_TO_MASTER_USER_SECRET_NAME
		)
})
public class ApiHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final List<String> STATUS_VALUES = Arrays.asList(
			"CREATED", "IN_TRANSIT", "DELAYED", "DELIVERED", "CANCELLED");

	@Override
	public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
		String method = request.getHttpMethod() == null ? "" : request.getHttpMethod().toUpperCase();
		String path = request.getPath() == null ? "" : request.getPath();
		Map<String, String> pathParams = request.getPathParameters() == null
				? new HashMap<>() : request.getPathParameters();
		String body = request.getBody();

		try {
			if (path.endsWith("/initdb") && "POST".equals(method)) {
				return initDb();
			}
			if (path.equals("/shipments") || path.endsWith("/shipments")) {
				if ("POST".equals(method)) {
					return createShipment(body);
				}
			}
			if (path.contains("/shipments/")) {
				String shipmentId = pathParams.get("shipmentId");
				if ("GET".equals(method)) {
					return getShipment(shipmentId);
				} else if ("PATCH".equals(method)) {
					return updateShipment(shipmentId, body);
				} else if ("DELETE".equals(method)) {
					return deleteShipment(shipmentId);
				}
			}
			if (path.equals("/carriers") || path.endsWith("/carriers")) {
				if ("POST".equals(method)) {
					return createCarrier(body);
				}
			}
			if (path.contains("/carriers/")) {
				String carrierId = pathParams.get("carrierId");
				if ("GET".equals(method)) {
					return getCarrier(carrierId);
				} else if ("PATCH".equals(method)) {
					return updateCarrier(carrierId, body);
				} else if ("DELETE".equals(method)) {
					return deleteCarrier(carrierId);
				}
			}
			if (path.equals("/statusupdates") || path.endsWith("/statusupdates")) {
				if ("POST".equals(method)) {
					return createStatusUpdate(body);
				}
			}
			if (path.contains("/statusupdates/")) {
				String shipmentId = pathParams.get("shipmentId");
				if ("GET".equals(method)) {
					return getStatusUpdates(shipmentId);
				}
			}
			return response(404, errorJson("Route not found: " + method + " " + path));
		} catch (Exception e) {
			context.getLogger().log("ERROR: " + e.getMessage());
			return response(500, errorJson(e.getMessage()));
		}
	}

	private APIGatewayProxyResponseEvent initDb() throws Exception {
		String createEnum =
				"DO $$ BEGIN " +
				"  IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'statustype') THEN " +
				"    CREATE TYPE StatusType AS ENUM ('CREATED','IN_TRANSIT','DELAYED','DELIVERED','CANCELLED'); " +
				"  END IF; " +
				"END $$;";

		String createShipments =
				"CREATE TABLE IF NOT EXISTS shipments (" +
				"  shipment_id VARCHAR(50) PRIMARY KEY," +
				"  order_id VARCHAR(50)," +
				"  origin VARCHAR(100)," +
				"  destination VARCHAR(100)," +
				"  weight_kg DECIMAL(10,2)," +
				"  created_at TIMESTAMP WITH TIME ZONE DEFAULT (now() AT TIME ZONE 'UTC')" +
				")";

		String createCarriers =
				"CREATE TABLE IF NOT EXISTS carriers (" +
				"  carrier_id VARCHAR(50) PRIMARY KEY," +
				"  name VARCHAR(100)," +
				"  email VARCHAR(100)," +
				"  phone VARCHAR(20)," +
				"  is_active BOOLEAN" +
				")";

		String createStatusUpdates =
				"CREATE TABLE IF NOT EXISTS status_updates (" +
				"  update_id SERIAL PRIMARY KEY," +
				"  shipment_id VARCHAR(50) REFERENCES shipments(shipment_id)," +
				"  carrier_id VARCHAR(50) REFERENCES carriers(carrier_id)," +
				"  status StatusType," +
				"  location VARCHAR(100)," +
				"  notes TEXT," +
				"  timestamp TIMESTAMP WITH TIME ZONE DEFAULT (now() AT TIME ZONE 'UTC')" +
				")";

		try (Connection conn = DbConfig.getConnection();
		     Statement stmt = conn.createStatement()) {
			stmt.execute(createEnum);
			stmt.execute(createShipments);
			stmt.execute(createCarriers);
			stmt.execute(createStatusUpdates);
		}
		return response(200, "{\"message\":\"Initialization is successful\"}");
	}

	private APIGatewayProxyResponseEvent createShipment(String body) throws Exception {
		JsonNode node = MAPPER.readTree(body);
		String sql = "INSERT INTO shipments (shipment_id, order_id, origin, destination, weight_kg, created_at) " +
				"VALUES (?, ?, ?, ?, ?, now() AT TIME ZONE 'UTC')";
		try (Connection conn = DbConfig.getConnection();
		     PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setString(1, textOrNull(node, "shipment_id"));
			ps.setString(2, textOrNull(node, "order_id"));
			ps.setString(3, textOrNull(node, "origin"));
			ps.setString(4, textOrNull(node, "destination"));
			if (node.hasNonNull("weight_kg")) {
				ps.setDouble(5, node.get("weight_kg").asDouble());
			} else {
				ps.setNull(5, Types.NUMERIC);
			}
			ps.executeUpdate();
		}
		return response(201, "{\"message\":\"Shipment created\"}");
	}

	private APIGatewayProxyResponseEvent getShipment(String shipmentId) throws Exception {
		String sql = "SELECT shipment_id, order_id, origin, destination, weight_kg, created_at " +
				"FROM shipments WHERE shipment_id = ?";
		try (Connection conn = DbConfig.getConnection();
		     PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setString(1, shipmentId);
			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next()) {
					return response(404, errorJson("Shipment not found"));
				}
				ObjectNode obj = MAPPER.createObjectNode();
				obj.put("shipment_id", rs.getString("shipment_id"));
				obj.put("order_id", rs.getString("order_id"));
				obj.put("origin", rs.getString("origin"));
				obj.put("destination", rs.getString("destination"));
				obj.put("weight_kg", rs.getDouble("weight_kg"));
				Timestamp ts = rs.getTimestamp("created_at");
				obj.put("created_at", ts == null ? null : ts.toInstant().toString());
				return response(200, MAPPER.writeValueAsString(obj));
			}
		}
	}

	private APIGatewayProxyResponseEvent updateShipment(String shipmentId, String body) throws Exception {
		JsonNode node = MAPPER.readTree(body);
		List<String> sets = new ArrayList<>();
		List<Object> params = new ArrayList<>();
		if (node.has("order_id")) { sets.add("order_id = ?"); params.add(node.get("order_id").asText(null)); }
		if (node.has("origin")) { sets.add("origin = ?"); params.add(node.get("origin").asText(null)); }
		if (node.has("destination")) { sets.add("destination = ?"); params.add(node.get("destination").asText(null)); }
		if (node.has("weight_kg")) { sets.add("weight_kg = ?"); params.add(node.get("weight_kg").asDouble()); }

		if (sets.isEmpty()) {
			return response(200, "{\"message\":\"Nothing to update\"}");
		}

		String sql = "UPDATE shipments SET " + String.join(", ", sets) + " WHERE shipment_id = ?";
		try (Connection conn = DbConfig.getConnection();
		     PreparedStatement ps = conn.prepareStatement(sql)) {
			for (int i = 0; i < params.size(); i++) {
				Object p = params.get(i);
				if (p instanceof Double) {
					ps.setDouble(i + 1, (Double) p);
				} else {
					ps.setString(i + 1, (String) p);
				}
			}
			ps.setString(params.size() + 1, shipmentId);
			ps.executeUpdate();
		}
		return response(200, "{\"message\":\"Shipment updated\"}");
	}

	private APIGatewayProxyResponseEvent deleteShipment(String shipmentId) throws Exception {
		try (Connection conn = DbConfig.getConnection();
		     PreparedStatement ps = conn.prepareStatement("DELETE FROM shipments WHERE shipment_id = ?")) {
			ps.setString(1, shipmentId);
			ps.executeUpdate();
		}
		return response(200, "{\"message\":\"Shipment deleted\"}");
	}

	private APIGatewayProxyResponseEvent createCarrier(String body) throws Exception {
		JsonNode node = MAPPER.readTree(body);
		String sql = "INSERT INTO carriers (carrier_id, name, email, phone, is_active) VALUES (?, ?, ?, ?, ?)";
		try (Connection conn = DbConfig.getConnection();
		     PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setString(1, textOrNull(node, "carrier_id"));
			ps.setString(2, textOrNull(node, "name"));
			ps.setString(3, textOrNull(node, "email"));
			ps.setString(4, textOrNull(node, "phone"));
			if (node.hasNonNull("is_active")) {
				ps.setBoolean(5, node.get("is_active").asBoolean());
			} else {
				ps.setNull(5, Types.BOOLEAN);
			}
			ps.executeUpdate();
		}
		return response(201, "{\"message\":\"Carrier created\"}");
	}

	private APIGatewayProxyResponseEvent getCarrier(String carrierId) throws Exception {
		String sql = "SELECT carrier_id, name, email, phone, is_active FROM carriers WHERE carrier_id = ?";
		try (Connection conn = DbConfig.getConnection();
		     PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setString(1, carrierId);
			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next()) {
					return response(404, errorJson("Carrier not found"));
				}
				ObjectNode obj = MAPPER.createObjectNode();
				obj.put("carrier_id", rs.getString("carrier_id"));
				obj.put("name", rs.getString("name"));
				obj.put("email", rs.getString("email"));
				obj.put("phone", rs.getString("phone"));
				obj.put("is_active", rs.getBoolean("is_active"));
				return response(200, MAPPER.writeValueAsString(obj));
			}
		}
	}

	private APIGatewayProxyResponseEvent updateCarrier(String carrierId, String body) throws Exception {
		JsonNode node = MAPPER.readTree(body);
		List<String> sets = new ArrayList<>();
		List<Object> params = new ArrayList<>();
		if (node.has("name")) { sets.add("name = ?"); params.add(node.get("name").asText(null)); }
		if (node.has("email")) { sets.add("email = ?"); params.add(node.get("email").asText(null)); }
		if (node.has("phone")) { sets.add("phone = ?"); params.add(node.get("phone").asText(null)); }
		if (node.has("is_active")) { sets.add("is_active = ?"); params.add(node.get("is_active").asBoolean()); }

		if (sets.isEmpty()) {
			return response(200, "{\"message\":\"Nothing to update\"}");
		}

		String sql = "UPDATE carriers SET " + String.join(", ", sets) + " WHERE carrier_id = ?";
		try (Connection conn = DbConfig.getConnection();
		     PreparedStatement ps = conn.prepareStatement(sql)) {
			for (int i = 0; i < params.size(); i++) {
				Object p = params.get(i);
				if (p instanceof Boolean) {
					ps.setBoolean(i + 1, (Boolean) p);
				} else {
					ps.setString(i + 1, (String) p);
				}
			}
			ps.setString(params.size() + 1, carrierId);
			ps.executeUpdate();
		}
		return response(200, "{\"message\":\"Carrier updated\"}");
	}

	private APIGatewayProxyResponseEvent deleteCarrier(String carrierId) throws Exception {
		try (Connection conn = DbConfig.getConnection();
		     PreparedStatement ps = conn.prepareStatement("DELETE FROM carriers WHERE carrier_id = ?")) {
			ps.setString(1, carrierId);
			ps.executeUpdate();
		}
		return response(200, "{\"message\":\"Carrier deleted\"}");
	}

	private APIGatewayProxyResponseEvent createStatusUpdate(String body) throws Exception {
		JsonNode node = MAPPER.readTree(body);
		String status = textOrNull(node, "status");
		if (status != null && !STATUS_VALUES.contains(status)) {
			return response(400, errorJson("Invalid status value: " + status));
		}
		String sql = "INSERT INTO status_updates (shipment_id, carrier_id, status, location, notes, timestamp) " +
				"VALUES (?, ?, ?::StatusType, ?, ?, now() AT TIME ZONE 'UTC')";
		try (Connection conn = DbConfig.getConnection();
		     PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setString(1, textOrNull(node, "shipment_id"));
			ps.setString(2, textOrNull(node, "carrier_id"));
			ps.setString(3, status);
			ps.setString(4, textOrNull(node, "location"));
			ps.setString(5, textOrNull(node, "notes"));
			ps.executeUpdate();
		}
		return response(201, "{\"message\":\"Status update created\"}");
	}

	private APIGatewayProxyResponseEvent getStatusUpdates(String shipmentId) throws Exception {
		String sql = "SELECT update_id, shipment_id, carrier_id, status, location, notes, timestamp " +
				"FROM status_updates WHERE shipment_id = ? ORDER BY timestamp";
		try (Connection conn = DbConfig.getConnection();
		     PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setString(1, shipmentId);
			try (ResultSet rs = ps.executeQuery()) {
				ArrayNode arr = MAPPER.createArrayNode();
				while (rs.next()) {
					ObjectNode obj = MAPPER.createObjectNode();
					obj.put("update_id", rs.getInt("update_id"));
					obj.put("shipment_id", rs.getString("shipment_id"));
					obj.put("carrier_id", rs.getString("carrier_id"));
					obj.put("status", rs.getString("status"));
					obj.put("location", rs.getString("location"));
					obj.put("notes", rs.getString("notes"));
					Timestamp ts = rs.getTimestamp("timestamp");
					obj.put("timestamp", ts == null ? null : ts.toInstant().toString());
					arr.add(obj);
				}
				return response(200, MAPPER.writeValueAsString(arr));
			}
		}
	}

	private static String textOrNull(JsonNode node, String field) {
		return node.hasNonNull(field) ? node.get(field).asText() : null;
	}

	private static APIGatewayProxyResponseEvent response(int statusCode, String body) {
		Map<String, String> headers = new HashMap<>();
		headers.put("Content-Type", "application/json");
		return new APIGatewayProxyResponseEvent()
				.withStatusCode(statusCode)
				.withHeaders(headers)
				.withBody(body);
	}

	private static String errorJson(String message) {
		return "{\"message\":\"" + (message == null ? "" : message.replace("\"", "\\\"")) + "\"}";
	}
}
