package com.railviz.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.railviz.model.RouteDTO;
import com.railviz.model.RouteWsEvent;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RouteService {
	private final SimpMessagingTemplate ws;
	private final Map<String, List<double[]>> store = new LinkedHashMap<>();

	@PostConstruct
	void seed() {
		if (!store.isEmpty()) {
			return;
		}
		store.put("T1", List.of(new double[] { 48.8829, 2.3335 }, new double[] { 48.8710, 2.3339 },
				new double[] { 48.8612, 2.3466 }, new double[] { 48.8530, 2.3499 }, new double[] { 48.8403, 2.3606 }));
		store.put("T2", List.of(new double[] { 48.9000, 2.2900 }, new double[] { 48.8900, 2.3100 },
				new double[] { 48.8750, 2.3400 }, new double[] { 48.8650, 2.3700 }, new double[] { 48.8550, 2.4000 }));
		store.put("T3", List.of(new double[] { 48.8700, 2.3700 }, new double[] { 48.8600, 2.3550 },
				new double[] { 48.8500, 2.3400 }, new double[] { 48.8400, 2.3300 }));
	}

	public List<RouteDTO> routes() {
		return store.entrySet().stream().map(e -> new RouteDTO(e.getKey(), e.getValue())).toList();
	}

	public void addRoute(RouteDTO r) {
		store.put(r.id(), r.points());
		ws.convertAndSend("/topic/routes", RouteWsEvent.add(r));
	}

	public void updateRoute(RouteDTO r) {
		if (!store.containsKey(r.id())) {
			throw new IllegalArgumentException("route inconnue");
		}
		store.put(r.id(), r.points());
		ws.convertAndSend("/topic/routes", RouteWsEvent.update(r));
	}

	public void deleteRoute(String id) {
		if (store.remove(id) != null) {
			ws.convertAndSend("/topic/routes", RouteWsEvent.delete(id));
		}
	}

	public List<double[]> get(String id) {
		return store.get(id);
	}

	public boolean exists(String id) {
		var r = store.get(id);
		return r != null && !r.isEmpty();
	}
}
