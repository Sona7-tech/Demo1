package com.task05;


import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.model.RetentionSetting;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.environment.EnvironmentVariables;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@LambdaHandler(
		lambdaName = "api_handler",
		roleName = "api_handler-role",
		isPublishVersion = true,
		aliasName = "${lambdas_alias_name}",
		logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED,
		memory = 256
)
@EnvironmentVariables(value = {
		@EnvironmentVariable(key = "table_name", value = "${target_table}"),
		@EnvironmentVariable(key = "region", value = "${region}")
})
public class ApiHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

	private static final String TABLE_NAME = System.getenv("table_name");
	private static final String REGION = System.getenv("region");

	private static final AmazonDynamoDBClientBuilder clientBuilder =
			AmazonDynamoDBClientBuilder.standard().withRegion(REGION);

	private static final DynamoDB dynamoDB = new DynamoDB(clientBuilder.build());
	private static final Table table = dynamoDB.getTable(TABLE_NAME);

	private final ObjectMapper mapper = new ObjectMapper();

	@Override
	public Map<String, Object> handleRequest(Map<String, Object> request, Context context) {

		Map<String, Object> response = new HashMap<>();

		try {
			context.getLogger().log("Request: " + request + "\n");

			String bodyStr = (String) request.get("body");
			Map<String, Object> body = mapper.readValue(bodyStr, Map.class);

			int principalId = Integer.parseInt(body.get("principalId").toString());
			Map<String, Object> content = (Map<String, Object>) body.get("content");

			String id = UUID.randomUUID().toString();
			String createdAt = Instant.now().toString();

			Map<String, Object> eventItem = new HashMap<>();
			eventItem.put("id", id);
			eventItem.put("principalId", principalId);
			eventItem.put("createdAt", createdAt);
			eventItem.put("body", content);

			Item item = new Item()
					.withPrimaryKey("id", id)
					.withInt("principalId", principalId)
					.withString("createdAt", createdAt)
					.withMap("body", content);

			table.putItem(item);


			Map<String, Object> responseBody = new HashMap<>();
			responseBody.put("statusCode", 201);
			responseBody.put("event", eventItem);

			Map<String, String> headers = new HashMap<>();
			headers.put("Content-Type", "application/json");

			response.put("statusCode", 201);
			response.put("headers", headers);
			response.put("body", mapper.writeValueAsString(responseBody));

			return response;

		} catch (Exception e) {
			context.getLogger().log("Error: " + e);

			response.put("statusCode", 500);

			Map<String, Object> error = new HashMap<>();
			error.put("message", "Internal Server Error");

			try {
				response.put("body", mapper.writeValueAsString(error));
			} catch (Exception ex) {
				response.put("body", "{\"message\":\"Internal Server Error\"}");
			}

			return response;
		}
	}
}