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

	@Override
	public Map<String, Object> handleRequest(Map<String, Object> request, Context context) {
		Map<String, Object> response = new HashMap<>();
		try {
			// Log environment variables
			context.getLogger().log("TABLE_NAME: " + TABLE_NAME + "\n");
			context.getLogger().log("REGION: " + REGION + "\n");

			if (TABLE_NAME == null || TABLE_NAME.isEmpty()) {
				context.getLogger().log("ERROR: table_name environment variable is not set!\n");
				response.put("statusCode", 500);
				response.put("body", "Internal Server Error: table_name env variable is missing");
				return response;
			}

			// Request parsing
			context.getLogger().log("Request: " + request + "\n");

			Integer principalId = Integer.valueOf(request.get("principalId").toString());
			context.getLogger().log("principalId: " + principalId + "\n");

			Map<String, String> content = (Map<String, String>) request.get("content");
			context.getLogger().log("content: " + content +
					"\n");

			String id = UUID.randomUUID().toString();
			String createdAt = Instant.now().toString();

			context.getLogger().log("Generated id: " + id + ", createdAt: " + createdAt + "\n");

			Map<String, Object> eventItem = new HashMap<>();
			eventItem.put("id", id);
			eventItem.put("principalId", principalId);
			eventItem.put("createdAt", createdAt);
			eventItem.put("body", content);

			// DynamoDB-yə yaz
			Item item = new Item()
					.withPrimaryKey("id", id)
					.withInt("principalId", principalId)
					.withString("createdAt", createdAt)
					.withMap("body", content);

			table.putItem(item);
			context.getLogger().log("Item successfully put to DynamoDB.\n");

			// body sahəsini JSON string kimi qaytar
			ObjectMapper mapper = new ObjectMapper();
			String bodyJson = mapper.writeValueAsString(eventItem);

			response.put("statusCode", 201);
			response.put("body", bodyJson);

			context.getLogger().log("Response: " + response + "\n");
			return response;

		} catch (Exception e) {
			context.getLogger().log("Error: " + e.getMessage() + "\n");
			for (StackTraceElement ste : e.getStackTrace()) {
				context.getLogger().log(ste.toString() + "\n");
			}
			response.put("statusCode", 500);
			response.put("body", "Internal Server Error");
			return response;
		}
	}
}