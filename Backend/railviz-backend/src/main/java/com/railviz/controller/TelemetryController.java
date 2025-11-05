package com.railviz.controller;

import java.util.Random;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Controller;

import com.railviz.model.TrainEvent;

@Controller
public class TelemetryController {
	private final SimpMessagingTemplate ws;
	private final Random rnd = new Random();

	public TelemetryController(SimpMessagingTemplate ws) {
		this.ws = ws;
	}

	@Scheduled(fixedRate = 200) // 5 Hz
	public void tick() {
		// EXEMPLE: génère 1 train qui avance sur une ligne imaginaire
		double lat = 48.8566 + rnd.nextGaussian() * 0.0005;
		double lon = 2.3522 + rnd.nextGaussian() * 0.0005;
		var ev = new TrainEvent("TGV-001", lat, lon, 140 + rnd.nextInt(10), "B-42",
				rnd.nextBoolean() ? "GREEN" : "YELLOW", System.currentTimeMillis());
		ws.convertAndSend("/topic/telemetry", ev);
	}
}
