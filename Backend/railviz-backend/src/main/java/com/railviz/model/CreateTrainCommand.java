package com.railviz.model;

public record CreateTrainCommand(String trainId, String routeId, Double lineSpeedKmh, // vitesse de ligne (cible GREEN)
		Integer startSeg, // optionnel: segment de départ
		Double startProgress // optionnel: progress 0..1
) {
}
