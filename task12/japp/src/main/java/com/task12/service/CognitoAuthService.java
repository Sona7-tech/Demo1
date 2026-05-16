package com.task12.service;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminConfirmSignUpRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminInitiateAuthRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminSetUserPasswordRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthFlowType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthenticationResultType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.MessageActionType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UsernameExistsException;

import java.util.Map;

public class CognitoAuthService {

	private final CognitoIdentityProviderClient cognito;
	private final String userPoolId;
	private final String clientId;

	public CognitoAuthService() {
		String region = System.getenv("REGION");
		this.userPoolId = System.getenv("COGNITO_ID");
		this.clientId = System.getenv("CLIENT_ID");
		this.cognito = CognitoIdentityProviderClient.builder()
				.region(Region.of(region))
				.build();
	}

	public void signUp(String firstName, String lastName, String email, String password) {
		try {
			cognito.adminCreateUser(AdminCreateUserRequest.builder()
					.userPoolId(userPoolId)
					.username(email)
					.userAttributes(
							AttributeType.builder().name("email").value(email).build(),
							AttributeType.builder().name("email_verified").value("true").build(),
							AttributeType.builder().name("given_name").value(firstName).build(),
							AttributeType.builder().name("family_name").value(lastName).build()
					)
					.messageAction(MessageActionType.SUPPRESS)
					.temporaryPassword(password)
					.build());

			cognito.adminSetUserPassword(AdminSetUserPasswordRequest.builder()
					.userPoolId(userPoolId)
					.username(email)
					.password(password)
					.permanent(true)
					.build());

			cognito.adminConfirmSignUp(AdminConfirmSignUpRequest.builder()
					.userPoolId(userPoolId)
					.username(email)
					.build());
		} catch (UsernameExistsException e) {
			throw new IllegalArgumentException("User already exists");
		}
	}

	public String signIn(String email, String password) {
		AuthenticationResultType authResult = cognito.adminInitiateAuth(AdminInitiateAuthRequest.builder()
				.userPoolId(userPoolId)
				.clientId(clientId)
				.authFlow(AuthFlowType.ADMIN_USER_PASSWORD_AUTH)
				.authParameters(Map.of(
						"USERNAME", email,
						"PASSWORD", password
				))
				.build())
				.authenticationResult();

		if (authResult == null || authResult.idToken() == null) {
			throw new IllegalArgumentException("Authentication failed");
		}
		return authResult.idToken();
	}
}
