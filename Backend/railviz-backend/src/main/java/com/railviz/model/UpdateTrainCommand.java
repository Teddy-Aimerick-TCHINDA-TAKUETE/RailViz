package com.railviz.model;

public record UpdateTrainCommand(String routeId, Double lineSpeedKmh, Integer startSeg, Double startProgress,
		Double accel, Double decel) {
}
