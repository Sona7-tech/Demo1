package com.task11;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.environment.EnvironmentVariables;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.lambda.LambdaProvisionedConcurrency;
import com.syndicate.deployment.model.ProvisionedConcurrencyType;
import com.syndicate.deployment.model.RetentionSetting;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.syndicate.deployment.model.environment.ValueTransformer.RDS_DB_CLUSTER_NAME_TO_ENDPOINT;
import static com.syndicate.deployment.model.environment.ValueTransformer.RDS_DB_CLUSTER_NAME_TO_MASTER_USER_SECRET_NAME;

@LambdaHandler(
    lambdaName = "api_handler",
	roleName = "api_handler-role",
	isPublishVersion = true,
	aliasName = "${lambdas_alias_name}",
	logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED,
	memory = 256,
	timeout = 300,
	subnetsIds = {"${lambda_sn_id}"},
	securityGroupIds = {"${logistic_sg_id}"}
		)
@EnvironmentVariables(value = {
		@EnvironmentVariable(key = "REGION", value = "${region}"),
		@EnvironmentVariable(
				key = "DB_ENDPOINT",
				value = "logistic-cluster",
				valueTransformer = RDS_DB_CLUSTER_NAME_TO_ENDPOINT
		),
		@EnvironmentVariable(key = "DB_USER", value = "postgres"),
		@EnvironmentVariable(key = "DB_PASSWORD", value = "emin12345")
})
public class ApiHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

	private final ApiService service = new ApiService();

	@Override
	public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent req, Context ctx) {

		String path = req.getPath();
		String method = req.getHttpMethod();

		try {

			// INIT DB
			if (path.equals("/initdb") && method.equals("POST")) {
				service.initDb();
				return response(200, "DB initialized");
			}

			// SHIPMENTS
			if (path.equals("/shipments") && method.equals("POST"))
				return response(201, service.createShipment(req.getBody()));

			if (path.startsWith("/shipments/") && method.equals("GET"))
				return response(200, service.getShipment(extractId(path)));

			if (path.startsWith("/shipments/") && method.equals("PATCH"))
				return response(200, service.updateShipment(extractId(path), req.getBody()));

			if (path.startsWith("/shipments/") && method.equals("DELETE")) {
				service.deleteShipment(extractId(path));
				return response(200, "Deleted");
			}

			// CARRIERS
			if (path.equals("/carriers") && method.equals("POST"))
				return response(201, service.createCarrier(req.getBody()));

			if (path.startsWith("/carriers/") && method.equals("GET"))
				return response(200, service.getCarrier(extractId(path)));

			if (path.startsWith("/carriers/") && method.equals("PATCH"))
				return response(200, service.updateCarrier(extractId(path), req.getBody()));

			if (path.startsWith("/carriers/") && method.equals("DELETE")) {
				service.deleteCarrier(extractId(path));
				return response(200, "Deleted");
			}

			// STATUS
			if (path.equals("/statusupdates") && method.equals("POST"))
				return response(201, service.createStatus(req.getBody()));

			if (path.startsWith("/statusupdates/") && method.equals("GET"))
				return response(200, service.getStatus(extractId(path)));

			return response(404, "Not Found");

		} catch (Exception e) {
			return response(500, e.getMessage());
		}
	}

	private String extractId(String path) {
		return path.split("/")[2];
	}

	private APIGatewayProxyResponseEvent response(int code, Object body) {
		return new APIGatewayProxyResponseEvent()
				.withStatusCode(code)
				.withBody(String.valueOf(body));
	}
}
