package com.task06;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.model.RetentionSetting;
import com.syndicate.deployment.annotations.events.DynamoDbTriggerEventSource;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.environment.EnvironmentVariables;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@LambdaHandler(
		lambdaName = "audit_producer",
		roleName = "audit_producer-role",
		isPublishVersion = true,
		aliasName = "${lambdas_alias_name}",
		logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED,
		memory = 256
)
@DynamoDbTriggerEventSource(
		targetTable = "Configuration",
		batchSize = 1
)
@EnvironmentVariables(value = {
		@EnvironmentVariable(key = "table_name", value = "${target_table}"),
		@EnvironmentVariable(key = "region", value = "${region}")
})
public class AuditProducer implements RequestHandler<DynamodbEvent, Void> {

	private final String auditTableName = System.getenv("table_name");
	private final AmazonDynamoDB client = AmazonDynamoDBClientBuilder.defaultClient();
	private final DynamoDB dynamoDB = new DynamoDB(client);

	@Override
	public Void handleRequest(DynamodbEvent event, Context context) {

		Table auditTable = dynamoDB.getTable(auditTableName);

		for (DynamodbEvent.DynamodbStreamRecord record : event.getRecords()) {

			String eventName = record.getEventName();

			Map<String, AttributeValue> newImage = record.getDynamodb().getNewImage();
			Map<String, AttributeValue> oldImage = record.getDynamodb().getOldImage();

			if ("INSERT".equals(eventName) && newImage != null) {

				String key = newImage.get("key").getS();
				int value = Integer.parseInt(newImage.get("value").getN());

				Item auditItem = new Item()
						.withPrimaryKey("id", UUID.randomUUID().toString())
						.withString("itemKey", key)
						.withString("modificationTime", Instant.now().toString())
						.withMap("newValue", Map.of(
								"key", key,
								"value", value
						));

				auditTable.putItem(auditItem);

			} else if ("MODIFY".equals(eventName)
					&& newImage != null
					&& oldImage != null) {

				String key = newImage.get("key").getS();

				int oldValue = Integer.parseInt(oldImage.get("value").getN());
				int newValue = Integer.parseInt(newImage.get("value").getN());

				if (oldValue != newValue) {

					Item auditItem = new Item()
							.withPrimaryKey("id", UUID.randomUUID().toString())
							.withString("itemKey", key)
							.withString("modificationTime", Instant.now().toString())
							.withString("updatedAttribute", "value")
							.withInt("oldValue", oldValue)
							.withInt("newValue", newValue);

					auditTable.putItem(auditItem);
				}
			}
		}

		return null;
	}
}