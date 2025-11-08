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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.railviz.model.CreateTrainCommand;
import com.railviz.model.TrainDTO;
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
		return trainService.list().stream().toList();
	}

	@PostMapping
	public ResponseEntity<?> create(@RequestBody CreateTrainCommand cmd) {
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

	@PutMapping("/{id}")
	public ResponseEntity<?> update(@PathVariable String id, @RequestBody CreateTrainCommand cmd) {
		if (!id.equals(cmd.trainId())) {
			return ResponseEntity.badRequest().body("id incohérent");
		}
		trainService.updateTrain(cmd.trainId(), cmd.routeId(), cmd.lineSpeedKmh(), cmd.startSeg(), cmd.startProgress());
		return ResponseEntity.ok().build();
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<?> delete(@PathVariable String id) {
		trainService.deleteTrain(id);
		return ResponseEntity.noContent().build();
	}
}
