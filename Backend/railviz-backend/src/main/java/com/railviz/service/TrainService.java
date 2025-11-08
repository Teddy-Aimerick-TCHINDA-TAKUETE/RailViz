package com.railviz.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.railviz.model.TrainDTO;
import com.railviz.model.TrainWsEvent;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TrainService {

	private final RouteService routeService;
	private final SimpMessagingTemplate ws;
	private final Random rnd = new Random();

	// Simulation: 0.8 s entre 2 ticks
	private static final double DT_SECONDS = 0.8;

	// Vitesse cible en fonction du signal
	private static final double V_YELLOW = 60.0; // km/h
	private static final double V_RED = 0.0; // km/h

	// Accélération / décélération (m/s²)
	private static final double ACCEL = 0.5;
	private static final double DECEL = 1.0;

	// Hystérésis (durées minimales par état)
	private static final long HOLD_GREEN = 8000;
	private static final long HOLD_YELLOW = 6000;
	private static final long HOLD_RED = 5000;

	private enum Sig {
		GREEN, YELLOW, RED
	}

	/** État interne d’un train */
	private static class TrainState {
		String id;
		String routeId;
		int seg; // segment courant (index dans la route)
		double prog; // progression [0..1] sur le segment
		double lineSpeedKmh; // vitesse de ligne cible quand GREEN
		double speedKmh; // vitesse instantanée
		double lat, lon; // position courante
		Sig signal; // état signal actuel
		long lastChangeMs; // dernier changement de signal

		TrainState(String id, String routeId, int seg, double prog, double lineSpeedKmh, double speedKmh, Sig signal,
				long t0) {
			this.id = id;
			this.routeId = routeId;
			this.seg = seg;
			this.prog = prog;
			this.lineSpeedKmh = lineSpeedKmh;
			this.speedKmh = speedKmh;
			this.signal = signal;
			this.lastChangeMs = t0;
		}
	}

	private final Map<String, TrainState> trains = new ConcurrentHashMap<>();

	@PostConstruct
	void seedDemo() {
		// (Optionnel) Démo de 3 trains si tu en veux au démarrage.
		// Commente si tu veux repartir à vide.
		if (routeService.exists("T1")) {
			create("TGV-001", "T1", 160, 0, 0.0);
		}
		if (routeService.exists("T2")) {
			create("TER-021", "T2", 100, 0, 0.0);
		}
		if (routeService.exists("T3")) {
			create("RER-A7", "T3", 70, 0, 0.0);
		}
	}

	/** Tick de simulation */
	@Scheduled(fixedRate = 800)
	public void tick() {
		trains.replaceAll((id, st) -> {
			TrainState ns = advance(st);
			// ws.convertAndSend("/topic/trains", toDTO(ns));
			ws.convertAndSend("/topic/telemetry", toDTO(ns));
			return ns;
		});
	}

	/** Avance un train d’un tick en respectant sa route et le signal. */
	private TrainState advance(TrainState s) {
		List<double[]> pts = routeService.get(s.routeId);
		if (pts == null || pts.size() < 2) {
			return s;
		}

		// 1) Hystérésis + aléas
		Sig nextSignal = evolveSignal(s.signal, s.lastChangeMs);
		long lastChange = (nextSignal != s.signal) ? System.currentTimeMillis() : s.lastChangeMs;

		// 2) Vitesse cible
		double targetKmh = switch (nextSignal) {
		case GREEN -> s.lineSpeedKmh;
		case YELLOW -> V_YELLOW;
		case RED -> V_RED;
		};

		// 3) Intégration accélération/décélération
		double v = s.speedKmh * 1000.0 / 3600.0; // m/s
		double vt = targetKmh * 1000.0 / 3600.0; // m/s
		double a = (vt > v) ? ACCEL : -DECEL;
		double v2 = v + a * DT_SECONDS;
		if ((vt > v && v2 > vt) || (vt < v && v2 < vt)) {
			v2 = vt; // clamp
		}
		double meters = Math.max(v2, 0) * DT_SECONDS;

		// 4) Avance sur la polyligne
		int seg = s.seg;
		double prog = s.prog;
		double[] A = pts.get(seg), B = pts.get(seg + 1);
		double segLen = haversine(A[0], A[1], B[0], B[1]);

		double newProg = prog + meters / Math.max(segLen, 1);
		int newSeg = seg;
		while (newProg >= 1.0) {
			newProg -= 1.0;
			newSeg++;
			if (newSeg >= pts.size() - 1) {
				newSeg = 0;
				newProg = 0.0;
			}
			A = pts.get(newSeg);
			B = pts.get(newSeg + 1);
			segLen = haversine(A[0], A[1], B[0], B[1]);
		}

		double lati = A[0] + (B[0] - A[0]) * newProg;
		double longi = A[1] + (B[1] - A[1]) * newProg;
		double speedKmhNow = v2 * 3.6;

		return new TrainState(s.id, s.routeId, newSeg, newProg, s.lineSpeedKmh, speedKmhNow, nextSignal, lastChange) {
			{
				this.lat = lati;
				this.lon = longi;
			}
		};
	}

	// Hystérésis + petites probabilités de transition
	private Sig evolveSignal(Sig current, long lastChangeMs) {
		long now = System.currentTimeMillis();
		long held = now - lastChangeMs;

		switch (current) {
		case GREEN -> {
			if (held < HOLD_GREEN) {
				return Sig.GREEN;
			}
			if (rnd.nextDouble() < 0.03) {
				return Sig.YELLOW;
			}
			return Sig.GREEN;
		}
		case YELLOW -> {
			if (held < HOLD_YELLOW) {
				return Sig.YELLOW;
			}
			if (rnd.nextDouble() < 0.15) {
				return Sig.RED;
			}
			return Sig.GREEN;
		}
		case RED -> {
			if (held < HOLD_RED) {
				return Sig.RED;
			}
			return Sig.YELLOW;
		}
		}
		return current;
	}

	// Haversine en mètres
	static double haversine(double lat1, double lon1, double lat2, double lon2) {
		double R = 6371e3;
		double p1 = Math.toRadians(lat1), p2 = Math.toRadians(lat2);
		double dp = Math.toRadians(lat2 - lat1), dl = Math.toRadians(lon2 - lon1);
		double a = Math.sin(dp / 2) * Math.sin(dp / 2)
				+ Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
		double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
		return R * c;
	}

	/*
	 * ====================== API utilisée par le contrôleur ======================
	 */

	public boolean exists(String id) {
		return trains.containsKey(id);
	}

	/** Liste des trains sous forme de DTO (pour GET /api/trains) */
	public Collection<TrainDTO> list() {
		return trains.values().stream().map(TrainService::toDTO).toList();
	}

	/** Création d’un train (utilisée par POST /api/trains) */
	public void create(String id, String routeId, double lineSpeedKmh, Integer startSeg, Double startProgress) {
		if (trains.containsKey(id)) {
			throw new IllegalArgumentException("trainId déjà utilisé");
		}
		List<double[]> pts = routeService.get(routeId);
		if (pts == null || pts.size() < 2) {
			throw new IllegalArgumentException("routeId inconnu");
		}

		int seg = (startSeg != null) ? startSeg : 0;
		double prog = (startProgress != null) ? Math.max(0, Math.min(1, startProgress)) : 0.0;
		double[] A = pts.get(seg), B = pts.get(seg + 1);
		double lat = A[0] + (B[0] - A[0]) * prog;
		double lon = A[1] + (B[1] - A[1]) * prog;

		var s = new TrainState(id, routeId, seg, prog, lineSpeedKmh, 0.0, Sig.GREEN, System.currentTimeMillis());
		s.lat = lat;
		s.lon = lon;

		trains.put(id, s);
		ws.convertAndSend("/topic/trains", TrainWsEvent.add(toDTO(s)));
	}

	public void updateTrain(String id, String routeId, double lineSpeedKmh, Integer startSeg, Double startProgress) {

		List<double[]> pts = routeService.get(routeId);

		int seg = (startSeg != null) ? startSeg : 0;
		double prog = (startProgress != null) ? Math.max(0, Math.min(1, startProgress)) : 0.0;
		double[] A = pts.get(seg), B = pts.get(seg + 1);
		double lat = A[0] + (B[0] - A[0]) * prog;
		double lon = A[1] + (B[1] - A[1]) * prog;

		var ts = new TrainState(id, routeId, seg, prog, lineSpeedKmh, 0.0, Sig.GREEN, System.currentTimeMillis());
		ts.lat = lat;
		ts.lon = lon;

		TrainDTO t = toDTO(ts);
		if (!trains.containsKey(t.id())) {
			throw new IllegalArgumentException("train inconnue");
		}
		trains.put(t.id(), ts);
		ws.convertAndSend("/topic/trains", TrainWsEvent.update(t));
	}

	public void deleteTrain(String id) {
		if (trains.remove(id) != null) {
			ws.convertAndSend("/topic/trains", TrainWsEvent.delete(id));
		}
	}

	/** Changer la vitesse de ligne (PATCH /api/trains/{id}/speed) */
	public void setSpeed(String id, double lineSpeedKmh) {
		trains.computeIfPresent(id, (k, st) -> {
			var ns = new TrainState(st.id, st.routeId, st.seg, st.prog, lineSpeedKmh, st.speedKmh, st.signal,
					st.lastChangeMs);
			ns.lat = st.lat;
			ns.lon = st.lon;
			return ns;
		});
	}

	/** Mapping interne -> DTO public */
	private static TrainDTO toDTO(TrainState s) {
		return new TrainDTO(s.id, s.lat, s.lon, s.speedKmh, s.signal.name());
	}
}
