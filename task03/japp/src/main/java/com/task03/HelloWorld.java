package com.task03;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.model.RetentionSetting;

import java.util.HashMap;
import java.util.Map;

@LambdaHandler(
    lambdaName = "hello_world",
	roleName = "hello_world-role",
	isPublishVersion = true,
	aliasName = "${lambdas_alias_name}",
	logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED,
	memory = 256
)
public class HelloWorld implements RequestHandler<Map<String, Object>, Map<String, Object>> {

	@Override
	public Map<String, Object> handleRequest(Map<String, Object> request, Context context) {

		Map<String, Object> body = new HashMap<>();
		body.put("statusCode", 200);
		body.put("message", "Hello from Lambda");

		Map<String, Object> response = new HashMap<>();
		response.put("statusCode", 200);
		response.put("body", body);
		response.put("headers", Map.of("content-type", "application/json"));
		response.put("isBase64Encoded", false);

		return response;
	}
}