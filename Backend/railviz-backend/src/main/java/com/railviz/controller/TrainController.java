package com.railviz.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.railviz.model.CreateTrainCommand;
import com.railviz.model.TrainDTO;
import com.railviz.model.UpdateTrainCommand;
import com.railviz.service.RouteService;
import com.railviz.service.TrainService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/trains")
@RequiredArgsConstructor
public class TrainController {

	private final RouteService routeService;
	private final TrainService trainService;

	@GetMapping
	public List<TrainDTO> list() {
		return trainService.list().stream().map(TrainService::toDto).toList();
	}

	@PostMapping
	public ResponseEntity<?> create(@RequestBody CreateTrainCommand cmd) {
		if (cmd.trainId() == null || cmd.trainId().isBlank()) {
			return ResponseEntity.badRequest().body("trainId requis");
		}
		if (!routeService.exists(cmd.routeId())) {
			return ResponseEntity.badRequest().body("routeId inconnu");
		}
		if (trainService.exists(cmd.trainId())) {
			return ResponseEntity.badRequest().body("trainId déjà utilisé");
		}
		trainService.create(cmd.trainId(), cmd.routeId(), cmd.lineSpeedKmh(), cmd.startSeg(), cmd.startProgress());
		return ResponseEntity.status(HttpStatus.CREATED).build();
	}

	@PatchMapping("/{id}/speed")
	public ResponseEntity<?> setSpeed(@PathVariable String id, @RequestBody Map<String, Object> body) {
		var v = (Number) body.get("lineSpeedKmh");
		if (v == null) {
			return ResponseEntity.badRequest().body("lineSpeedKmh requis");
		}
		trainService.setSpeed(id, v.doubleValue());
		return ResponseEntity.ok().build();
	}

	@PatchMapping("/{id}")
	public ResponseEntity<?> update(@PathVariable String id, @RequestBody UpdateTrainCommand cmd) {
		var current = trainService.findDto(id);
		if (current == null) {
			return ResponseEntity.notFound().build();
		}

		if (cmd.lineSpeedKmh() != null) {
			trainService.setSpeed(id, cmd.lineSpeedKmh());
		}
		if (cmd.accel() != null && cmd.decel() != null) {
			trainService.setAccelDecel(id, cmd.accel(), cmd.decel());
		}
		if (cmd.routeId() != null) {
			if (!routeService.exists(cmd.routeId())) {
				return ResponseEntity.badRequest().body("nouvelle route inconnue");
			}
			// repositionnement (optionnel) via startSeg/startProgress
			double vKmh = (cmd.lineSpeedKmh() != null) ? cmd.lineSpeedKmh() : current.speedKmh();
			trainService.updateTrain(id, cmd.routeId(), vKmh, cmd.startSeg(), cmd.startProgress());
		}
		return ResponseEntity.ok().build();
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<?> delete(@PathVariable String id) {
		var current = trainService.findDto(id);
		if (current == null) {
			return ResponseEntity.notFound().build();
		}
		trainService.deleteTrain(id);
		return ResponseEntity.noContent().build();
	}
}
