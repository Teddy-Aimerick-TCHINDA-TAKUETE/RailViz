package com.railviz.model;

public record TrainEvent(String trainId, double lat, double lon, double speedKmh, String blockId, // canton/segment
		String signalState, // GREEN | YELLOW | RED
		long timestamp // epoch ms
) {

}
