package com.task12.service;

import com.task12.model.ReservationRecord;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ReservationService {

	private final DynamoDbClient dynamoDb;
	private final String tableName;

	public ReservationService() {
		String region = System.getenv("REGION");
		this.tableName = System.getenv("reservations_table");
		this.dynamoDb = DynamoDbClient.builder()
				.region(Region.of(region))
				.build();
	}

	public List<ReservationRecord> listReservations() {
		List<ReservationRecord> reservations = new ArrayList<>();
		dynamoDb.scan(ScanRequest.builder().tableName(tableName).build())
				.items()
				.forEach(item -> reservations.add(fromItem(item)));
		return reservations;
	}

	public String createReservation(ReservationRecord reservation) throws IllegalStateException {
		if (hasConflict(reservation)) {
			throw new IllegalStateException("Conflicting reservation");
		}
		String reservationId = UUID.randomUUID().toString();
		Map<String, AttributeValue> item = toItem(reservationId, reservation);
		dynamoDb.putItem(PutItemRequest.builder()
				.tableName(tableName)
				.item(item)
				.build());
		return reservationId;
	}

	private boolean hasConflict(ReservationRecord reservation) {
		for (ReservationRecord existing : listReservations()) {
			if (existing.getTableNumber() != reservation.getTableNumber()) {
				continue;
			}
			if (!existing.getDate().equals(reservation.getDate())) {
				continue;
			}
			if (timesOverlap(existing.getSlotTimeStart(), existing.getSlotTimeEnd(),
					reservation.getSlotTimeStart(), reservation.getSlotTimeEnd())) {
				return true;
			}
		}
		return false;
	}

	private boolean timesOverlap(String start1, String end1, String start2, String end2) {
		return start1.compareTo(end2) < 0 && start2.compareTo(end1) < 0;
	}

	private Map<String, AttributeValue> toItem(String id, ReservationRecord reservation) {
		Map<String, AttributeValue> item = new HashMap<>();
		item.put("id", AttributeValue.builder().s(id).build());
		item.put("tableNumber", AttributeValue.builder().n(String.valueOf(reservation.getTableNumber())).build());
		item.put("clientName", AttributeValue.builder().s(reservation.getClientName()).build());
		item.put("phoneNumber", AttributeValue.builder().s(reservation.getPhoneNumber()).build());
		item.put("date", AttributeValue.builder().s(reservation.getDate()).build());
		item.put("slotTimeStart", AttributeValue.builder().s(reservation.getSlotTimeStart()).build());
		item.put("slotTimeEnd", AttributeValue.builder().s(reservation.getSlotTimeEnd()).build());
		return item;
	}

	private ReservationRecord fromItem(Map<String, AttributeValue> item) {
		return new ReservationRecord(
				Integer.parseInt(item.get("tableNumber").n()),
				item.get("clientName").s(),
				item.get("phoneNumber").s(),
				item.get("date").s(),
				item.get("slotTimeStart").s(),
				item.get("slotTimeEnd").s()
		);
	}
}
