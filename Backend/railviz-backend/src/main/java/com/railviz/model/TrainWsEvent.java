package com.railviz.model;

public record TrainWsEvent(String type, TrainDTO train) {
	public static TrainWsEvent add(TrainDTO t) {
		return new TrainWsEvent("ADD", t);
	}

	public static TrainWsEvent update(TrainDTO t) {
		return new TrainWsEvent("UPDATE", t);
	}

	public static TrainWsEvent delete(String id) {
		return new TrainWsEvent("DELETE", new TrainDTO(id, 0, 0, 0, "", ""));
	}
}
