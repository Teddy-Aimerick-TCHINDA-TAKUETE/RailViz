package com.railviz.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.railviz.model.RouteDTO;
import com.railviz.service.RouteService;
import com.railviz.service.TrainService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/routes")
@RequiredArgsConstructor
public class RouteController {

	private final RouteService routeService;
	private final TrainService trainService;

	@GetMapping
	public List<RouteDTO> list() {
		return routeService.routes();
	}

	@PostMapping
	public ResponseEntity<?> add(@RequestBody RouteDTO route) {
		if (route.id() == null || route.points() == null || route.points().size() < 2) {
			return ResponseEntity.badRequest().body("id + >=2 points requis");
		}
		routeService.addRoute(route);
		trainService.onRoutesChangedExternally();
		return ResponseEntity.status(HttpStatus.CREATED).build();
	}

	@PutMapping("/{id}")
	public ResponseEntity<?> update(@PathVariable String id, @RequestBody RouteDTO route) {
		if (!id.equals(route.id())) {
			return ResponseEntity.badRequest().body("id path != id body");
		}
		if (route.points() == null || route.points().size() < 2) {
			return ResponseEntity.badRequest().body(">=2 points requis");
		}
		routeService.updateRoute(route);
		trainService.onRoutesChangedExternally();
		return ResponseEntity.ok().build();
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<?> delete(@PathVariable String id) {
		if (trainService.hasTrainsOnRoute(id)) {
			return ResponseEntity.status(HttpStatus.CONFLICT)
					.body("Impossible : des trains utilisent encore la route " + id);
		}
		routeService.deleteRoute(id);
		trainService.onRoutesChangedExternally();
		return ResponseEntity.noContent().build();
	}
}
