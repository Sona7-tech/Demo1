package com.task10;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.environment.EnvironmentVariables;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.lambda.LambdaUrlConfig;
import com.syndicate.deployment.model.RetentionSetting;
import com.syndicate.deployment.model.TracingMode;
import com.syndicate.deployment.model.lambda.url.AuthType;
import com.syndicate.deployment.model.lambda.url.InvokeMode;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@LambdaHandler(
    lambdaName = "processor",
	roleName = "processor-role",
	isPublishVersion = true,
	aliasName = "${lambdas_alias_name}",
	logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED,
	memory = 256,
	timeout = 60,
	tracingMode = TracingMode.Active
)

@LambdaUrlConfig(
		authType = AuthType.NONE,
		invokeMode = InvokeMode.BUFFERED
)
@EnvironmentVariables(value = {
		@EnvironmentVariable(key = "target_table", value = "${target_table}")
})
public class Processor implements RequestHandler<Object, Map<String, Object>> {

	private static final String WEATHER_URL =
			"https://api.open-meteo.com/v1/forecast"
					+ "?latitude=52.52"
					+ "&longitude=13.41"
					+ "&current=temperature_2m,wind_speed_10m"
					+ "&hourly=temperature_2m,relative_humidity_2m,wind_speed_10m";

	private final HttpClient httpClient = HttpClient.newBuilder()
			.version(HttpClient.Version.HTTP_1_1)
			.connectTimeout(Duration.ofSeconds(30))
			.build();

	private final ObjectMapper objectMapper = new ObjectMapper();

	private final DynamoDbClient dynamoDbClient = DynamoDbClient.builder()
			.region(Region.of(System.getenv().getOrDefault("AWS_REGION", "eu-west-1")))
			.build();

	public Map<String, Object> handleRequest(Object request, Context context) {
		Map<String, Object> response = new HashMap<>();
		try {
			String body = fetchWeather();
			JsonNode forecast = objectMapper.readTree(body);

			String id = UUID.randomUUID().toString();
			String tableName = System.getenv("target_table");

			Map<String, AttributeValue> item = new HashMap<>();
			item.put("id", AttributeValue.builder().s(id).build());
			item.put("forecast", AttributeValue.builder().m(buildForecast(forecast)).build());

			dynamoDbClient.putItem(PutItemRequest.builder()
					.tableName(tableName)
					.item(item)
					.build());

			Map<String, Object> payload = new HashMap<>();
			payload.put("id", id);
			payload.put("forecast", objectMapper.convertValue(forecast, Map.class));

			response.put("statusCode", 200);
			response.put("body", objectMapper.writeValueAsString(payload));
			return response;
		} catch (Exception e) {
			context.getLogger().log("Error: " + e.getMessage());
			response.put("statusCode", 500);
			response.put("body", "{\"message\": \"Internal server error\"}");
			return response;
		}
	}

	private String fetchWeather() throws Exception {
		HttpRequest httpRequest = HttpRequest.newBuilder()
				.uri(URI.create(WEATHER_URL))
				.timeout(Duration.ofSeconds(45))
				.header("Accept", "application/json")
				.GET()
				.build();
		HttpResponse<String> httpResponse =
				httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
		return httpResponse.body();
	}

	private Map<String, AttributeValue> buildForecast(JsonNode root) {
		Map<String, AttributeValue> forecast = new HashMap<>();

		putIfPresent(forecast, "elevation", root.get("elevation"));
		putIfPresent(forecast, "generationtime_ms", root.get("generationtime_ms"));
		putIfPresent(forecast, "latitude", root.get("latitude"));
		putIfPresent(forecast, "longitude", root.get("longitude"));
		putIfPresent(forecast, "timezone", root.get("timezone"));
		putIfPresent(forecast, "timezone_abbreviation", root.get("timezone_abbreviation"));
		putIfPresent(forecast, "utc_offset_seconds", root.get("utc_offset_seconds"));

		JsonNode hourly = root.get("hourly");
		if (hourly != null) {
			Map<String, AttributeValue> hourlyMap = new HashMap<>();
			hourlyMap.put("temperature_2m", numberList(hourly.get("temperature_2m")));
			hourlyMap.put("time", stringList(hourly.get("time")));
			forecast.put("hourly", AttributeValue.builder().m(hourlyMap).build());
		}

		JsonNode hourlyUnits = root.get("hourly_units");
		if (hourlyUnits != null) {
			Map<String, AttributeValue> unitsMap = new HashMap<>();
			putIfPresent(unitsMap, "temperature_2m", hourlyUnits.get("temperature_2m"));
			putIfPresent(unitsMap, "time", hourlyUnits.get("time"));
			forecast.put("hourly_units", AttributeValue.builder().m(unitsMap).build());
		}

		return forecast;
	}

	private void putIfPresent(Map<String, AttributeValue> map, String key, JsonNode node) {
		if (node == null || node.isNull()) {
			return;
		}
		if (node.isNumber()) {
			map.put(key, AttributeValue.builder().n(node.asText()).build());
		} else {
			map.put(key, AttributeValue.builder().s(node.asText()).build());
		}
	}

	private AttributeValue numberList(JsonNode array) {
		List<AttributeValue> values = new ArrayList<>();
		if (array != null && array.isArray()) {
			Iterator<JsonNode> it = array.elements();
			while (it.hasNext()) {
				values.add(AttributeValue.builder().n(it.next().asText()).build());
			}
		}
		return AttributeValue.builder().l(values).build();
	}

	private AttributeValue stringList(JsonNode array) {
		List<AttributeValue> values = new ArrayList<>();
		if (array != null && array.isArray()) {
			Iterator<JsonNode> it = array.elements();
			while (it.hasNext()) {
				values.add(AttributeValue.builder().s(it.next().asText()).build());
			}
		}
		return AttributeValue.builder().l(values).build();
	}
}
