package com.railviz.model;

public record TrainDTO(String id, double lat, double lon, double speedKmh, String signal, String routeId) {
}
