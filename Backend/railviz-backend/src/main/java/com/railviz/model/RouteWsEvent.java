package com.railviz.model;

public record RouteWsEvent(String type, RouteDTO route) {
	public static RouteWsEvent add(RouteDTO r) {
		return new RouteWsEvent("ADD", r);
	}

	public static RouteWsEvent update(RouteDTO r) {
		return new RouteWsEvent("UPDATE", r);
	}

	public static RouteWsEvent delete(String id) {
		return new RouteWsEvent("DELETE", new RouteDTO(id, java.util.List.of()));
	}
}
