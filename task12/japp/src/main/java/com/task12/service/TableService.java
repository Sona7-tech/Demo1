package com.task12.service;

import com.task12.model.TableRecord;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class TableService {

	private final DynamoDbClient dynamoDb;
	private final String tableName;

	public TableService() {
		String region = System.getenv("REGION");
		this.tableName = System.getenv("tables_table");
		this.dynamoDb = DynamoDbClient.builder()
				.region(Region.of(region))
				.build();
	}

	public List<TableRecord> listTables() {
		List<TableRecord> tables = new ArrayList<>();
		dynamoDb.scan(ScanRequest.builder().tableName(tableName).build())
				.items()
				.forEach(item -> tables.add(fromItem(item)));
		tables.sort(Comparator.comparingInt(TableRecord::getId));
		return tables;
	}

	public Optional<TableRecord> getTable(String tableId) {
		Map<String, AttributeValue> key = Map.of("id", AttributeValue.builder().s(tableId).build());
		Map<String, AttributeValue> item = dynamoDb.getItem(GetItemRequest.builder()
				.tableName(tableName)
				.key(key)
				.build()).item();
		if (item == null || item.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(fromItem(item));
	}

	public Optional<TableRecord> findByNumber(int number) {
		return listTables().stream()
				.filter(t -> t.getNumber() == number)
				.findFirst();
	}

	public void createTable(TableRecord table) throws IllegalStateException {
		if (getTable(String.valueOf(table.getId())).isPresent()) {
			throw new IllegalStateException("Table already exists");
		}
		dynamoDb.putItem(PutItemRequest.builder()
				.tableName(tableName)
				.item(toItem(table))
				.build());
	}

	private Map<String, AttributeValue> toItem(TableRecord table) {
		Map<String, AttributeValue> item = new HashMap<>();
		item.put("id", AttributeValue.builder().s(String.valueOf(table.getId())).build());
		item.put("number", AttributeValue.builder().n(String.valueOf(table.getNumber())).build());
		item.put("places", AttributeValue.builder().n(String.valueOf(table.getPlaces())).build());
		item.put("isVip", AttributeValue.builder().bool(table.isVip()).build());
		if (table.getMinOrder() != null) {
			item.put("minOrder", AttributeValue.builder().n(String.valueOf(table.getMinOrder())).build());
		}
		return item;
	}

	private TableRecord fromItem(Map<String, AttributeValue> item) {
		int id = Integer.parseInt(item.get("id").s());
		int number = Integer.parseInt(item.get("number").n());
		int places = Integer.parseInt(item.get("places").n());
		boolean isVip = item.get("isVip").bool();
		Integer minOrder = item.containsKey("minOrder")
				? Integer.parseInt(item.get("minOrder").n())
				: null;
		return new TableRecord(id, number, places, isVip, minOrder);
	}
}
