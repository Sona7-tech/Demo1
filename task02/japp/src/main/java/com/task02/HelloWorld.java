package com.task02;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.lambda.LambdaUrlConfig;
import com.syndicate.deployment.model.RetentionSetting;
import com.syndicate.deployment.model.lambda.url.AuthType;
import com.syndicate.deployment.model.lambda.url.InvokeMode;

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
@LambdaUrlConfig(
	authType = AuthType.NONE,
	invokeMode = InvokeMode.BUFFERED
)
public class HelloWorld implements RequestHandler<Map<String, Object>, Map<String, Object>> {

	@Override
	public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
		String path = (String) event.get("rawPath");
		Map<String, Object> requestContext = (Map<String, Object>) event.get("requestContext");
		String method = (String) ((Map<String, Object>) requestContext.get("http")).get("method");

		if ("/hello".equals(path) && "GET".equals(method)) {
			return Map.of(
					"statusCode", 200,
					"body", "{\"statusCode\":200,\"message\":\"Hello from Lambda\"}"
			);
		} else {
			return Map.of(
					"statusCode", 400,
					"body", String.format("{\"statusCode\":400,\"message\":\"Bad Request: %s %s\"}", method, path)
			);
		}
	}
}
