package com.task04;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.model.RetentionSetting;
import com.syndicate.deployment.annotations.events.SnsEventSource;
import java.util.HashMap;
import java.util.Map;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
@LambdaHandler(
    lambdaName = "sns_handler",
	roleName = "sns_handler-role",
	isPublishVersion = true,
	aliasName = "${lambdas_alias_name}",
	logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED,
	memory = 256
)

@SnsEventSource(
		targetTopic = "lambda_topic"
)
public class SnsHandler implements RequestHandler<SNSEvent, Map<String, Object>> {
	public Map<String, Object> handleRequest(SNSEvent event, Context context) {
		for (SNSEvent.SNSRecord record : event.getRecords()) {
			System.out.println("Received SNS message: " + record.getSNS().getMessage());
		}
		Map<String, Object> resultMap = new HashMap<>();
		resultMap.put("statusCode", 200);
		resultMap.put("body", "Processed SNS message");
		return resultMap;
	}
}
