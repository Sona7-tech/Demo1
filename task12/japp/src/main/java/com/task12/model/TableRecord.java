package com.task12.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TableRecord {

	private int id;
	private int number;
	private int places;
	@JsonProperty("isVip")
	private boolean isVip;
	private Integer minOrder;

	public TableRecord() {
	}

	public TableRecord(int id, int number, int places, boolean isVip, Integer minOrder) {
		this.id = id;
		this.number = number;
		this.places = places;
		this.isVip = isVip;
		this.minOrder = minOrder;
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public int getNumber() {
		return number;
	}

	public void setNumber(int number) {
		this.number = number;
	}

	public int getPlaces() {
		return places;
	}

	public void setPlaces(int places) {
		this.places = places;
	}

	public boolean isVip() {
		return isVip;
	}

	public void setVip(boolean vip) {
		isVip = vip;
	}

	public Integer getMinOrder() {
		return minOrder;
	}

	public void setMinOrder(Integer minOrder) {
		this.minOrder = minOrder;
	}
}
