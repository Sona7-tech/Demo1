package com.task12.util;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

public final class ApiResponseFactory {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private ApiResponseFactory() {
	}

	public static APIGatewayProxyResponseEvent ok(Object body) {
		return json(200, body);
	}

	public static APIGatewayProxyResponseEvent okEmpty() {
		return json(200, new HashMap<>());
	}

	public static APIGatewayProxyResponseEvent badRequest() {
		return json(400, new HashMap<>());
	}

	public static APIGatewayProxyResponseEvent json(int statusCode, Object body) {
		Map<String, String> headers = new HashMap<>();
		headers.put("Content-Type", "application/json");
		try {
			return new APIGatewayProxyResponseEvent()
					.withStatusCode(statusCode)
					.withHeaders(headers)
					.withBody(MAPPER.writeValueAsString(body))
					.withIsBase64Encoded(false);
		} catch (JsonProcessingException e) {
			return new APIGatewayProxyResponseEvent()
					.withStatusCode(500)
					.withHeaders(headers)
					.withBody("{\"message\":\"Internal error\"}")
					.withIsBase64Encoded(false);
		}
	}
}
