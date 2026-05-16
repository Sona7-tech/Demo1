package com.task11;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;

import com.syndicate.deployment.annotations.events.S3EventSource;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.model.RetentionSetting;
import com.amazonaws.services.s3.model.S3Object;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;



@LambdaHandler(
    lambdaName = "batch_processor",
	roleName = "batch_processor-role",
	isPublishVersion = true,
	aliasName = "${lambdas_alias_name}",
	logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED,
	memory = 256,
	timeout = 120,
	subnetsIds = {"${lambda_sn_id}"},
	securityGroupIds = {"${logistic_sg_id}"}
)

@S3EventSource(
		targetBucket = "data-transfer-storage",
		events = {"s3:ObjectCreated:*"}
)
public class BatchProcessor implements RequestHandler<S3Event, Map<String, Object>> {

	private final ETLService etlService = new ETLService();

	@Override
	public Map<String, Object> handleRequest(S3Event s3Event, Context context) {

		AmazonS3 s3Client = AmazonS3ClientBuilder.standard()
				.withRegion("eu-west-1")
				.build();

		Map<String, Object> response = new HashMap<>();
		int processed = 0;

		for (S3Event.S3EventNotificationRecord record : s3Event.getRecords()) {

			String bucketName = record.getS3().getBucket().getName();

			String objectKey = java.net.URLDecoder.decode(
					record.getS3().getObject().getKey(),
					java.nio.charset.StandardCharsets.UTF_8
			);

			try (S3Object s3Object = s3Client.getObject(bucketName, objectKey);
			     BufferedReader reader = new BufferedReader(
						 new InputStreamReader(s3Object.getObjectContent())
				 );
			     Connection conn = DbConfig.getConnection()
			) {

				if (objectKey.contains("shipments")) {
					etlService.loadShipments(reader, conn);
				} else if (objectKey.contains("carriers")) {
					etlService.loadCarriers(reader, conn);
				} else if (objectKey.contains("status_updates")) {
					etlService.loadStatusUpdates(reader, conn);
				}

				processed++;

			} catch (Exception e) {
				context.getLogger().log("ERROR: " + e.getMessage());
			}
		}

		response.put("status", "success");
		response.put("processedRecords", processed);

		return response;
	}
}