package com.task08;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.environment.EnvironmentVariables;
import com.syndicate.deployment.annotations.events.RuleEventSource;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.model.RetentionSetting;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@LambdaHandler(
		lambdaName = "uuid_generator",
		roleName = "uuid_generator-role",
		isPublishVersion = true,
		aliasName = "${lambdas_alias_name}",
		logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED,
		memory = 256
)

@RuleEventSource(
		targetRule = "uuid_trigger"
)

@EnvironmentVariables(value = {
		@EnvironmentVariable(key = "target_bucket", value = "${target_bucket}")
})
public class UuidGenerator implements RequestHandler<ScheduledEvent, Map<String, Object>> {

	private final AmazonS3 s3Client = AmazonS3ClientBuilder.defaultClient();

	private static final String BUCKET_NAME =
			System.getenv("target_bucket");

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public Map<String, Object> handleRequest(ScheduledEvent request, Context context) {

		try {

			String executionTime = Instant.now().toString();

			List<String> ids = new ArrayList<>();

			for (int i = 0; i < 10; i++) {
				ids.add(UUID.randomUUID().toString());
			}

			Map<String, Object> body = new HashMap<>();
			body.put("ids", ids);

			String jsonBody = objectMapper.writeValueAsString(body);

			byte[] contentBytes = jsonBody.getBytes(StandardCharsets.UTF_8);

			ObjectMetadata metadata = new ObjectMetadata();
			metadata.setContentLength(contentBytes.length);
			metadata.setContentType("application/json");

			s3Client.putObject(
					BUCKET_NAME,
					executionTime + ".json",
					new ByteArrayInputStream(contentBytes),
					metadata
			);

			Map<String, Object> response = new HashMap<>();
			response.put("statusCode", 200);
			response.put("body", jsonBody);

			return response;

		} catch (Exception e) {

			Map<String, Object> error = new HashMap<>();
			error.put("statusCode", 500);
			error.put("body", e.getMessage());

			return error;
		}
	}
}
