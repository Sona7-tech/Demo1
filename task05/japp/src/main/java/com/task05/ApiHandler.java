package com.task05;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.model.RetentionSetting;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.time.Instant;

@LambdaHandler(
    lambdaName = "api_handler",
	roleName = "api_handler-role",
	isPublishVersion = true,
	aliasName = "${lambdas_alias_name}",
	logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED,
	memory = 256
)
public class ApiHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

	private final String tableName = System.getenv("target_table"); // "Events"
	private final DynamoDB dynamoDB = new DynamoDB(AmazonDynamoDBClientBuilder.defaultClient());

	@Override
	public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
		try {
			String bodyStr = (String) input.get("body");
			ObjectMapper mapper = new ObjectMapper();
			Map<String, Object> body = mapper.readValue(bodyStr, Map.class);

			// principalId Double kimi gələ bilər
			Number principalIdNum = (Number) body.get("principalId");
			int principalId = principalIdNum.intValue();

			Map<String, String> content = (Map<String, String>) body.get("content");

			String id = UUID.randomUUID().toString();
			String createdAt = Instant.now().toString();

			Table table = dynamoDB.getTable(tableName);
			Item item = new Item()
					.withPrimaryKey("id", id)
					.withInt("principalId", principalId)
					.withString("createdAt", createdAt)
					.withMap("body", content);

			table.putItem(item);

			Map<String, Object> event = new HashMap<>();
			event.put("id", id);
			event.put("principalId", principalId);
			event.put("createdAt", createdAt);
			event.put("body", content);

			Map<String, Object> response = new HashMap<>();
			response.put("statusCode", 201);
			response.put("event", event);

			return response;
		} catch (Exception e) {
			Map<String, Object> error = new HashMap<>();
			error.put("statusCode", 500);
			error.put("message", "Internal server error: " + e.getMessage());
			return error;
		}
	}
}
