package com.task04;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.model.RetentionSetting;
import com.syndicate.deployment.annotations.events.SqsTriggerEventSource;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import java.util.HashMap;
import java.util.Map;

@LambdaHandler(
    lambdaName = "sqs_handler",
	roleName = "sqs_handler-role",
	isPublishVersion = true,
	aliasName = "${lambdas_alias_name}",
	logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED,
	memory = 256
)

@SqsTriggerEventSource(
		targetQueue = "async_queue",
		batchSize = 10
)
public class SqsHandler implements RequestHandler<SQSEvent, Map<String, Object>> {
	public Map<String, Object> handleRequest(SQSEvent event, Context context) {
		for (SQSEvent.SQSMessage msg : event.getRecords()) {
			System.out.println(msg.getBody());
		}
		Map<String, Object> resultMap = new HashMap<>();
		resultMap.put("statusCode", 200);
		resultMap.put("message", "Processed SQS message");
		return resultMap;
	}
}
