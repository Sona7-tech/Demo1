package com.task12.util;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.regex.Pattern;

public final class ValidationUtil {

	private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
	private static final Pattern SIGNUP_PASSWORD = Pattern.compile("^[a-zA-Z0-9$%^*\\-_]{12,}$");
	private static final Pattern SIGNIN_PASSWORD = Pattern.compile("^[a-zA-Z0-9$%^*\\-_]{12,}$");
	private static final Pattern DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
	private static final Pattern TIME = Pattern.compile("^\\d{2}:\\d{2}$");

	private ValidationUtil() {
	}

	public static boolean isBlank(String value) {
		return value == null || value.trim().isEmpty();
	}

	public static boolean isValidEmail(String email) {
		return !isBlank(email) && EMAIL.matcher(email).matches();
	}

	public static boolean isValidSignupPassword(String password) {
		return password != null && SIGNUP_PASSWORD.matcher(password).matches();
	}

	public static boolean isValidSigninPassword(String password) {
		return password != null && SIGNIN_PASSWORD.matcher(password).matches();
	}

	public static boolean hasTextField(JsonNode node, String field) {
		return node != null && node.has(field) && !node.get(field).isNull()
				&& node.get(field).isTextual() && !node.get(field).asText().trim().isEmpty();
	}

	public static boolean hasIntField(JsonNode node, String field) {
		return node != null && node.has(field) && !node.get(field).isNull() && node.get(field).isInt();
	}

	public static boolean hasBooleanField(JsonNode node, String field) {
		return node != null && node.has(field) && !node.get(field).isNull() && node.get(field).isBoolean();
	}

	public static boolean isValidDate(String date) {
		return !isBlank(date) && DATE.matcher(date).matches();
	}

	public static boolean isValidTime(String time) {
		return !isBlank(time) && TIME.matcher(time).matches();
	}

	public static boolean isTimeRangeValid(String start, String end) {
		return start.compareTo(end) < 0;
	}
}
