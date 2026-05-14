package com.task09;

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
		lambdaName = "api_handler",
		roleName = "api_handler-role",
		isPublishVersion = true,
		aliasName = "${lambdas_alias_name}",
		logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED,
		memory = 256,
		layers = {"weather_sdk_layer"}
)
@LambdaUrlConfig(
		authType = AuthType.NONE,
		invokeMode = InvokeMode.BUFFERED
)
public class ApiHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

	@Override
	public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {

		Map<String, Object> response = new HashMap<>();

		try {

			String path = (String) event.get("rawPath");

			String method = "GET";

			Map<String, Object> requestContext =
					(Map<String, Object>) event.get("requestContext");

			if (requestContext != null) {

				Map<String, Object> http =
						(Map<String, Object>) requestContext.get("http");

				if (http != null) {
					method = (String) http.get("method");
				}
			}

			if (!"/weather".equals(path) || !"GET".equals(method)) {

				response.put("statusCode", 400);

				response.put(
						"body",
						String.format(
								"{\"statusCode\": 400, \"message\": \"Bad request syntax or unsupported method. Request path: %s. HTTP method: %s\"}",
								path,
								method
						)
				);

				return response;
			}

			WeatherClient weatherClient = new WeatherClient();

			String weatherData = weatherClient.getWeatherForecast();

			Map<String, String> headers = new HashMap<>();
			headers.put("Content-Type", "application/json");

			response.put("statusCode", 200);
			response.put("headers", headers);
			response.put("body", weatherData);

			return response;

		} catch (Exception e) {

			response.put("statusCode", 500);

			response.put(
					"body",
					"{\"message\": \"Internal server error\"}"
			);

			return response;
		}
	}
}