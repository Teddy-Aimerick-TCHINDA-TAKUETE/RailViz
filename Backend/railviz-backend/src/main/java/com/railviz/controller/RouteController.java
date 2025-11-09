package com.railviz.controller;

import java.util.List;

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

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/routes")
@RequiredArgsConstructor
public class RouteController {

	private final RouteService routeService;

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
		return ResponseEntity.ok().build();
	}

	@PutMapping("/{id}")
	public ResponseEntity<?> update(@PathVariable String id, @RequestBody RouteDTO route) {
		if (!id.equals(route.id())) {
			return ResponseEntity.badRequest().body("id incohérent");
		}
		routeService.updateRoute(route);
		return ResponseEntity.ok().build();
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<?> delete(@PathVariable String id) {
		routeService.deleteRoute(id);
		return ResponseEntity.noContent().build();
	}
}
