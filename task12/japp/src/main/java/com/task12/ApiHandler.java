package com.task12;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.environment.EnvironmentVariables;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.resources.DependsOn;
import com.syndicate.deployment.model.ResourceType;
import com.syndicate.deployment.model.RetentionSetting;
import com.task12.model.ReservationRecord;
import com.task12.model.TableRecord;
import com.task12.service.CognitoAuthService;
import com.task12.service.ReservationService;
import com.task12.service.TableService;
import com.task12.util.ApiResponseFactory;
import com.task12.util.ValidationUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.syndicate.deployment.model.environment.ValueTransformer.USER_POOL_NAME_TO_CLIENT_ID;
import static com.syndicate.deployment.model.environment.ValueTransformer.USER_POOL_NAME_TO_USER_POOL_ID;

@LambdaHandler(
		lambdaName = "api_handler",
		roleName = "api_handler-role",
		isPublishVersion = true,
		aliasName = "${lambdas_alias_name}",
		logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED,
		memory = 256
)
@DependsOn(resourceType = ResourceType.COGNITO_USER_POOL, name = "${booking_userpool}")
@DependsOn(resourceType = ResourceType.DYNAMODB_TABLE, name = "${tables_table}")
@DependsOn(resourceType = ResourceType.DYNAMODB_TABLE, name = "${reservations_table}")
@EnvironmentVariables(value = {
		@EnvironmentVariable(key = "REGION", value = "${region}"),
		@EnvironmentVariable(
				key = "COGNITO_ID",
				value = "${booking_userpool}",
				valueTransformer = USER_POOL_NAME_TO_USER_POOL_ID
		),
		@EnvironmentVariable(
				key = "CLIENT_ID",
				value = "${booking_userpool}",
				valueTransformer = USER_POOL_NAME_TO_CLIENT_ID
		),
		@EnvironmentVariable(key = "tables_table", value = "${tables_table}"),
		@EnvironmentVariable(key = "reservations_table", value = "${reservations_table}")
})
public class ApiHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final CognitoAuthService cognitoAuthService = new CognitoAuthService();
	private final TableService tableService = new TableService();
	private final ReservationService reservationService = new ReservationService();

	@Override
	public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
		String resource = normalizeResource(request.getResource());
		String method = request.getHttpMethod();

		try {
			if ("/signup".equals(resource) && "POST".equals(method)) {
				return handleSignUp(request);
			}
			if ("/signin".equals(resource) && "POST".equals(method)) {
				return handleSignIn(request);
			}
			if ("/tables".equals(resource) && "GET".equals(method)) {
				return handleListTables();
			}
			if ("/tables".equals(resource) && "POST".equals(method)) {
				return handleCreateTable(request);
			}
			if ("/tables/{tableId}".equals(resource) && "GET".equals(method)) {
				return handleGetTable(request);
			}
			if ("/reservations".equals(resource) && "GET".equals(method)) {
				return handleListReservations();
			}
			if ("/reservations".equals(resource) && "POST".equals(method)) {
				return handleCreateReservation(request);
			}
			return ApiResponseFactory.badRequest();
		} catch (Exception e) {
			context.getLogger().log("Error: " + e.getMessage());
			return ApiResponseFactory.badRequest();
		}
	}

	private String normalizeResource(String resource) {
		if (resource == null) {
			return "";
		}
		if (resource.startsWith("/api/")) {
			return resource.substring(4);
		}
		return resource;
	}

	private APIGatewayProxyResponseEvent handleSignUp(APIGatewayProxyRequestEvent request) throws Exception {
		JsonNode body = parseBody(request);
		if (body == null
				|| !ValidationUtil.hasTextField(body, "firstName")
				|| !ValidationUtil.hasTextField(body, "lastName")
				|| !ValidationUtil.hasTextField(body, "email")
				|| !ValidationUtil.hasTextField(body, "password")) {
			return ApiResponseFactory.badRequest();
		}

		String firstName = body.get("firstName").asText();
		String lastName = body.get("lastName").asText();
		String email = body.get("email").asText();
		String password = body.get("password").asText();

		if (!ValidationUtil.isValidEmail(email) || !ValidationUtil.isValidSignupPassword(password)) {
			return ApiResponseFactory.badRequest();
		}

		cognitoAuthService.signUp(firstName, lastName, email, password);
		return ApiResponseFactory.okEmpty();
	}

	private APIGatewayProxyResponseEvent handleSignIn(APIGatewayProxyRequestEvent request) throws Exception {
		JsonNode body = parseBody(request);
		if (body == null
				|| !ValidationUtil.hasTextField(body, "email")
				|| !ValidationUtil.hasTextField(body, "password")) {
			return ApiResponseFactory.badRequest();
		}

		String email = body.get("email").asText();
		String password = body.get("password").asText();

		if (!ValidationUtil.isValidEmail(email) || !ValidationUtil.isValidSigninPassword(password)) {
			return ApiResponseFactory.badRequest();
		}

		String idToken = cognitoAuthService.signIn(email, password);
		Map<String, String> response = new HashMap<>();
		response.put("idToken", idToken);
		return ApiResponseFactory.ok(response);
	}

	private APIGatewayProxyResponseEvent handleListTables() {
		Map<String, Object> response = new HashMap<>();
		response.put("tables", tableService.listTables());
		return ApiResponseFactory.ok(response);
	}

	private APIGatewayProxyResponseEvent handleCreateTable(APIGatewayProxyRequestEvent request) throws Exception {
		JsonNode body = parseBody(request);
		if (body == null
				|| !ValidationUtil.hasIntField(body, "id")
				|| !ValidationUtil.hasIntField(body, "number")
				|| !ValidationUtil.hasIntField(body, "places")
				|| !ValidationUtil.hasBooleanField(body, "isVip")) {
			return ApiResponseFactory.badRequest();
		}

		Integer minOrder = null;
		if (body.has("minOrder") && !body.get("minOrder").isNull()) {
			if (!body.get("minOrder").isInt()) {
				return ApiResponseFactory.badRequest();
			}
			minOrder = body.get("minOrder").asInt();
		}

		TableRecord table = new TableRecord(
				body.get("id").asInt(),
				body.get("number").asInt(),
				body.get("places").asInt(),
				body.get("isVip").asBoolean(),
				minOrder
		);

		try {
			tableService.createTable(table);
		} catch (IllegalStateException e) {
			return ApiResponseFactory.badRequest();
		}

		Map<String, Integer> response = new HashMap<>();
		response.put("id", table.getId());
		return ApiResponseFactory.ok(response);
	}

	private APIGatewayProxyResponseEvent handleGetTable(APIGatewayProxyRequestEvent request) {
		Map<String, String> pathParams = request.getPathParameters();
		if (pathParams == null || !pathParams.containsKey("tableId")) {
			return ApiResponseFactory.badRequest();
		}

		String tableId = pathParams.get("tableId");
		Optional<TableRecord> table = tableService.getTable(tableId);
		return table.map(ApiResponseFactory::ok).orElseGet(ApiResponseFactory::badRequest);
	}

	private APIGatewayProxyResponseEvent handleListReservations() {
		List<ReservationRecord> reservations = reservationService.listReservations();
		Map<String, Object> response = new HashMap<>();
		response.put("reservations", reservations);
		return ApiResponseFactory.ok(response);
	}

	private APIGatewayProxyResponseEvent handleCreateReservation(APIGatewayProxyRequestEvent request) throws Exception {
		JsonNode body = parseBody(request);
		if (body == null
				|| !ValidationUtil.hasIntField(body, "tableNumber")
				|| !ValidationUtil.hasTextField(body, "clientName")
				|| !ValidationUtil.hasTextField(body, "phoneNumber")
				|| !ValidationUtil.hasTextField(body, "date")
				|| !ValidationUtil.hasTextField(body, "slotTimeStart")
				|| !ValidationUtil.hasTextField(body, "slotTimeEnd")) {
			return ApiResponseFactory.badRequest();
		}

		String date = body.get("date").asText();
		String slotTimeStart = body.get("slotTimeStart").asText();
		String slotTimeEnd = body.get("slotTimeEnd").asText();

		if (!ValidationUtil.isValidDate(date)
				|| !ValidationUtil.isValidTime(slotTimeStart)
				|| !ValidationUtil.isValidTime(slotTimeEnd)
				|| !ValidationUtil.isTimeRangeValid(slotTimeStart, slotTimeEnd)) {
			return ApiResponseFactory.badRequest();
		}

		int tableNumber = body.get("tableNumber").asInt();
		if (tableService.findByNumber(tableNumber).isEmpty()) {
			return ApiResponseFactory.badRequest();
		}

		ReservationRecord reservation = new ReservationRecord(
				tableNumber,
				body.get("clientName").asText(),
				body.get("phoneNumber").asText(),
				date,
				slotTimeStart,
				slotTimeEnd
		);

		try {
			String reservationId = reservationService.createReservation(reservation);
			Map<String, String> response = new HashMap<>();
			response.put("reservationId", reservationId);
			return ApiResponseFactory.ok(response);
		} catch (IllegalStateException e) {
			return ApiResponseFactory.badRequest();
		}
	}

	private JsonNode parseBody(APIGatewayProxyRequestEvent request) throws Exception {
		String body = request.getBody();
		if (ValidationUtil.isBlank(body)) {
			return null;
		}
		return MAPPER.readTree(body);
	}
}
