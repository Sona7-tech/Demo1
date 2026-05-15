package com.task11;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification.S3EventNotificationRecord;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.environment.EnvironmentVariables;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.model.RetentionSetting;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.syndicate.deployment.model.environment.ValueTransformer.RDS_DB_CLUSTER_NAME_TO_ENDPOINT;
import static com.syndicate.deployment.model.environment.ValueTransformer.RDS_DB_CLUSTER_NAME_TO_MASTER_USER_SECRET_NAME;

@LambdaHandler(
    lambdaName = "batch_processor",
	roleName = "batch_processor-role",
	isPublishVersion = true,
	aliasName = "${lambdas_alias_name}",
	logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED,
	memory = 256,
	timeout = 120,
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
public class BatchProcessor implements RequestHandler<S3Event, Map<String, Object>> {

	private static final int BATCH_SIZE = 500;

	private static final S3Client S3 = S3Client.builder()
			.region(Region.of(System.getenv().getOrDefault("REGION",
					System.getenv().getOrDefault("AWS_REGION", "eu-west-1"))))
			.build();

	@Override
	public Map<String, Object> handleRequest(S3Event event, Context context) {
		Map<String, Object> response = new HashMap<>();
		try {
			for (S3EventNotificationRecord record : event.getRecords()) {
				String bucket = record.getS3().getBucket().getName();
				String key = URLDecoder.decode(
						record.getS3().getObject().getKey().replace('+', ' '),
						StandardCharsets.UTF_8);
				context.getLogger().log("Processing s3://" + bucket + "/" + key);
				processFile(bucket, key, context);
			}
			response.put("statusCode", 200);
			response.put("body", "{\"message\":\"Processed\"}");
		} catch (Exception e) {
			context.getLogger().log("ERROR: " + e.getMessage());
			response.put("statusCode", 500);
			response.put("body", "{\"message\":\"" + e.getMessage() + "\"}");
		}
		return response;
	}

	private void processFile(String bucket, String key, Context context) throws Exception {
		String lowered = key.toLowerCase();
		String fileType;
		if (lowered.endsWith("shipments.csv") || lowered.contains("shipments")) {
			fileType = "shipments";
		} else if (lowered.endsWith("carriers.csv") || lowered.contains("carriers")) {
			fileType = "carriers";
		} else if (lowered.endsWith("status_updates.csv") || lowered.contains("status_updates")
				|| lowered.contains("statusupdates")) {
			fileType = "status_updates";
		} else {
			context.getLogger().log("Unknown file type, skipping: " + key);
			return;
		}

		GetObjectRequest get = GetObjectRequest.builder().bucket(bucket).key(key).build();
		try (ResponseInputStream<GetObjectResponse> in = S3.getObject(get);
		     BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {

			String headerLine = reader.readLine();
			if (headerLine == null) {
				context.getLogger().log("Empty file: " + key);
				return;
			}
			List<String> headers = parseCsvLine(headerLine);

			switch (fileType) {
				case "shipments":
					loadShipments(reader, headers, context);
					break;
				case "carriers":
					loadCarriers(reader, headers, context);
					break;
				case "status_updates":
					loadStatusUpdates(reader, headers, context);
					break;
				default:
					break;
			}
		}
	}

	private void loadShipments(BufferedReader reader, List<String> headers, Context context) throws Exception {
		String sql = "INSERT INTO shipments (shipment_id, order_id, origin, destination, weight_kg, created_at) " +
				"VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (shipment_id) DO NOTHING";
		try (Connection conn = DbConfig.getConnection();
		     PreparedStatement ps = conn.prepareStatement(sql)) {
			conn.setAutoCommit(false);
			int count = 0;
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.trim().isEmpty()) continue;
				Map<String, String> row = toMap(headers, parseCsvLine(line));
				ps.setString(1, row.get("shipment_id"));
				ps.setString(2, row.get("order_id"));
				ps.setString(3, row.get("origin"));
				ps.setString(4, row.get("destination"));
				String weight = row.get("weight_kg");
				if (weight == null || weight.isEmpty()) {
					ps.setNull(5, Types.NUMERIC);
				} else {
					ps.setDouble(5, Double.parseDouble(weight));
				}
				Timestamp ts = parseTimestamp(row.get("created_at"));
				if (ts == null) {
					ps.setNull(6, Types.TIMESTAMP);
				} else {
					ps.setTimestamp(6, ts);
				}
				ps.addBatch();
				count++;
				if (count % BATCH_SIZE == 0) {
					ps.executeBatch();
					conn.commit();
				}
			}
			ps.executeBatch();
			conn.commit();
			context.getLogger().log("Loaded " + count + " shipment rows");
		}
	}

	private void loadCarriers(BufferedReader reader, List<String> headers, Context context) throws Exception {
		String sql = "INSERT INTO carriers (carrier_id, name, email, phone, is_active) " +
				"VALUES (?, ?, ?, ?, ?) ON CONFLICT (carrier_id) DO NOTHING";
		try (Connection conn = DbConfig.getConnection();
		     PreparedStatement ps = conn.prepareStatement(sql)) {
			conn.setAutoCommit(false);
			int count = 0;
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.trim().isEmpty()) continue;
				Map<String, String> row = toMap(headers, parseCsvLine(line));
				ps.setString(1, row.get("carrier_id"));
				ps.setString(2, row.get("name"));
				ps.setString(3, row.get("email"));
				ps.setString(4, row.get("phone"));
				String active = row.get("is_active");
				if (active == null || active.isEmpty()) {
					ps.setNull(5, Types.BOOLEAN);
				} else {
					ps.setBoolean(5, parseBoolean(active));
				}
				ps.addBatch();
				count++;
				if (count % BATCH_SIZE == 0) {
					ps.executeBatch();
					conn.commit();
				}
			}
			ps.executeBatch();
			conn.commit();
			context.getLogger().log("Loaded " + count + " carrier rows");
		}
	}

	private void loadStatusUpdates(BufferedReader reader, List<String> headers, Context context) throws Exception {
		String sql = "INSERT INTO status_updates (shipment_id, carrier_id, status, location, notes, timestamp) " +
				"VALUES (?, ?, ?::StatusType, ?, ?, ?)";
		try (Connection conn = DbConfig.getConnection();
		     PreparedStatement ps = conn.prepareStatement(sql)) {
			conn.setAutoCommit(false);
			int count = 0;
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.trim().isEmpty()) continue;
				Map<String, String> row = toMap(headers, parseCsvLine(line));
				ps.setString(1, row.get("shipment_id"));
				ps.setString(2, row.get("carrier_id"));
				ps.setString(3, row.get("status"));
				ps.setString(4, row.get("location"));
				ps.setString(5, row.get("notes"));
				Timestamp ts = parseTimestamp(row.get("timestamp"));
				if (ts == null) {
					ps.setNull(6, Types.TIMESTAMP);
				} else {
					ps.setTimestamp(6, ts);
				}
				ps.addBatch();
				count++;
				if (count % BATCH_SIZE == 0) {
					ps.executeBatch();
					conn.commit();
				}
			}
			ps.executeBatch();
			conn.commit();
			context.getLogger().log("Loaded " + count + " status_update rows");
		}
	}

	private static Map<String, String> toMap(List<String> headers, List<String> values) {
		Map<String, String> map = new HashMap<>();
		for (int i = 0; i < headers.size(); i++) {
			String val = i < values.size() ? values.get(i) : null;
			map.put(headers.get(i).trim(), val);
		}
		return map;
	}

	private static List<String> parseCsvLine(String line) {
		List<String> result = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		boolean inQuotes = false;
		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);
			if (inQuotes) {
				if (c == '"') {
					if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
						current.append('"');
						i++;
					} else {
						inQuotes = false;
					}
				} else {
					current.append(c);
				}
			} else {
				if (c == '"') {
					inQuotes = true;
				} else if (c == ',') {
					result.add(current.toString());
					current.setLength(0);
				} else {
					current.append(c);
				}
			}
		}
		result.add(current.toString());
		return result;
	}

	private static Timestamp parseTimestamp(String value) {
		if (value == null || value.isEmpty()) return null;
		try {
			return Timestamp.from(Instant.parse(value));
		} catch (Exception ignored) {}
		try {
			return Timestamp.from(LocalDateTime.parse(value).toInstant(ZoneOffset.UTC));
		} catch (Exception ignored) {}
		List<String> patterns = Arrays.asList(
				"yyyy-MM-dd HH:mm:ss",
				"yyyy-MM-dd'T'HH:mm:ss",
				"yyyy-MM-dd'T'HH:mm:ss.SSSSSS",
				"yyyy-MM-dd'T'HH:mm:ss.SSS"
		);
		for (String p : patterns) {
			try {
				LocalDateTime ldt = LocalDateTime.parse(value,
						java.time.format.DateTimeFormatter.ofPattern(p));
				return Timestamp.from(ldt.toInstant(ZoneOffset.UTC));
			} catch (Exception ignored) {}
		}
		return null;
	}

	private static boolean parseBoolean(String value) {
		if (value == null) return false;
		String v = value.trim().toLowerCase();
		return v.equals("true") || v.equals("1") || v.equals("yes") || v.equals("t");
	}
}
