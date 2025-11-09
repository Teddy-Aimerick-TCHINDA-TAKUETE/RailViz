package com.railviz.service;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.railviz.model.RouteDTO;
import com.railviz.model.TrainDTO;
import com.railviz.model.TrainWsEvent;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TrainService {

	private final RouteService routeService;
	private final SimpMessagingTemplate ws;

	/** Tick (s) */
	private static final double DT = 0.25;

	/** Énumérations d’état */
	public static enum Phase {
		DWELL, ACCEL, CRUISE, DECEL
	}

	public static enum Sig {
		GREEN, YELLOW, RED
	}

	/** Cache par route : points + cumul des distances */
	static final class RouteGeom {
		final List<double[]> pts;
		final double[] cum; // cum[i] = distance (m) du point i depuis le début
		final double length; // longueur totale (m)

		RouteGeom(List<double[]> pts, double[] cum, double length) {
			this.pts = pts;
			this.cum = cum;
			this.length = length;
		}
	}

	/** État d’un train (en mètres / m/s / secondes) */
	public static final class TrainState {
		String id;
		String routeId;
		double s; // distance courante le long de la route [0..L]
		int dir; // +1 aller, -1 retour
		double v; // vitesse instantanée (m/s)
		double vMax; // vitesse de pointe (m/s)
		double a; // accélération (m/s²)
		double d; // décélération (m/s²) (positif, on soustrait)
		Phase phase; // DWELL / ACCEL / CRUISE / DECEL
		long dwellUntil; // timestamp fin de DWELL, si phase=DWELL
		double decelStart;

		TrainState(String id, String routeId, double s, int dir, double vMax, double a, double d) {
			this.id = id;
			this.routeId = routeId;
			this.s = s;
			this.dir = dir;
			this.v = 0.0;
			this.vMax = vMax;
			this.a = a;
			this.d = d;
			this.phase = Phase.DWELL;
			this.dwellUntil = System.currentTimeMillis(); // sera ajusté
			this.decelStart = 0;
		}
	}

	private final Map<String, TrainState> trains = new ConcurrentHashMap<>();
	private final Map<String, RouteGeom> routes = new ConcurrentHashMap<>();

	/** Paramètres globaux (peuvent être mis par train si tu veux) */
	private static final long DWELL_MS = 5000; // arrêt à chaque extrémité
	private static final double DEFAULT_A = 0.5; // m/s²
	private static final double DEFAULT_D = 0.9; // m/s² (freine un peu plus fort)

	/** Utilitaires géométrie */
	private static double hav(double lat1, double lon1, double lat2, double lon2) {
		double R = 6371e3;
		double p1 = Math.toRadians(lat1), p2 = Math.toRadians(lat2);
		double dp = Math.toRadians(lat2 - lat1), dl = Math.toRadians(lon2 - lon1);
		double a = Math.sin(dp / 2) * Math.sin(dp / 2)
				+ Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
		return 2 * R * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
	}

	private static RouteGeom buildGeom(List<double[]> pts) {
		double[] cum = new double[pts.size()];
		double acc = 0;
		cum[0] = 0;
		for (int i = 0; i < pts.size() - 1; i++) {
			double[] A = pts.get(i), B = pts.get(i + 1);
			acc += hav(A[0], A[1], B[0], B[1]);
			cum[i + 1] = acc;
		}
		return new RouteGeom(pts, cum, acc);
	}

	/** interpolation : à partir de s (m), retourne lat/lon */
	private static double[] pointAt(RouteGeom g, double s) {
		if (s <= 0) {
			return g.pts.get(0);
		}
		if (s >= g.length) {
			return g.pts.get(g.pts.size() - 1);
		}
		// binaire ou linéaire: ici linéaire
		int i = Arrays.binarySearch(g.cum, s);
		if (i >= 0) {
			return g.pts.get(i);
		}
		int j = -i - 1; // insertion point
		int i0 = j - 1;
		double s0 = g.cum[i0], s1 = g.cum[j];
		double t = (s - s0) / (s1 - s0 + 1e-9);
		double[] A = g.pts.get(i0), B = g.pts.get(j);
		double lat = A[0] + (B[0] - A[0]) * t;
		double lon = A[1] + (B[1] - A[1]) * t;
		return new double[] { lat, lon };
	}

	/** init routes + (facultatif) quelques trains */
	@PostConstruct
	void init() {
		// charger/cacher les routes existantes
		for (RouteDTO r : routeService.routes()) {
			routes.put(r.id(), buildGeom(r.points()));
		}
		// tu peux supprimer ces trains seed si tu veux
		if (routes.containsKey("T1")) {
			trains.put("TGV-001", new TrainState("TGV-001", "T1", 0, +1, 160 / 3.6, DEFAULT_A, DEFAULT_D));
		}
		if (routes.containsKey("T2")) {
			trains.put("TER-021", new TrainState("TER-021", "T2", 0, +1, 100 / 3.6, DEFAULT_A, DEFAULT_D));
		}
		if (routes.containsKey("T3")) {
			trains.put("RER-A7", new TrainState("RER-A7", "T3", 0, +1, 70 / 3.6, DEFAULT_A, DEFAULT_D));
		}

		// démarrer en dwell
		long now = System.currentTimeMillis();
		for (var t : trains.values()) {
			t.dwellUntil = now + DWELL_MS;
		}
	}

	/**
	 * Synchroniser le cache route à chaque modification côté RouteService (si tu
	 * ajoutes/édites)
	 */
	public void onRoutesChangedExternally() {
		routes.clear();
		for (RouteDTO r : routeService.routes()) {
			routes.put(r.id(), buildGeom(r.points()));
		}
	}

	/** Tick de simulation */
	@Scheduled(fixedRate = (long) (DT * 1000))
	public void tick() {
		long now = System.currentTimeMillis();

		trains.replaceAll((id, st) -> advance(now, st));

		// push WS
		for (var st : trains.values()) {
			var geom = routes.get(st.routeId);
			if (geom == null) {
				continue;
			}
			double[] P = pointAt(geom, st.s);
			Sig sig = switch (st.phase) {
			case DWELL -> Sig.RED;
			case ACCEL, DECEL -> Sig.YELLOW;
			case CRUISE -> Sig.GREEN;
			};
			var dto = new TrainDTO(st.id, P[0], P[1], st.v * 3.6, sig.name(), st.routeId);
			ws.convertAndSend("/topic/telemetry", dto);
		}
	}

	private TrainState advance(long now, TrainState t) {
		var geom = routes.get(t.routeId);
		if (geom == null || geom.length < 1) {
			return t;
		}

		// Déterminer si on est proche d’une extrémité
		boolean atStart = t.s <= 0.001;
		boolean atEnd = t.s >= geom.length - 0.001;

		switch (t.phase) {
		case DWELL -> {
			t.v = 0;
			if (now >= t.dwellUntil) {
				// On repart : si on est à une extrémité, on choisit la bonne direction
				if (atStart) {
					t.dir = +1;
				} else if (atEnd) {
					t.dir = -1;
				}
				t.phase = Phase.ACCEL;
			}
		}
		case ACCEL -> {
			// vitesse cible = vMax, mais on prévoit le freinage pour s'arrêter à
			// l'extrémité
			double sToEnd = (t.dir > 0) ? (geom.length - t.s) : t.s;
			// distance minimale de freinage depuis vitesse v vers 0 : v^2 / (2d)
			double minBrake = (t.v * t.v) / (2 * t.d);

			// accélère
			double v0 = t.v;
			t.v = t.v + t.a * DT;
			if (t.v > t.vMax) {
				t.v = t.vMax;
			}

			// si la place restante ne permet plus de continuer à accélérer -> passer en
			// DECEL
			if (sToEnd <= minBrake + 1.5 * t.v * DT) {
				t.phase = Phase.DECEL;
			}

			// avancer
			t.s += t.dir * (v0 * DT + 0.5 * t.a * DT * DT);
			// clamp
			if (t.s <= 0) {
				t.s = 0;
				t.phase = Phase.DECEL;
			}
			if (t.s >= geom.length) {
				t.s = geom.length;
				t.phase = Phase.DECEL;
			}

			// si on a atteint vMax et assez de distance restante -> CRUISE
			double minBrakeAtVmax = (t.vMax * t.vMax) / (2 * t.d);
			if (t.v >= t.vMax - 1e-6 && sToEnd > minBrakeAtVmax + 1.5 * t.vMax * DT) {
				t.phase = Phase.CRUISE;
			}
		}
		case CRUISE -> {
			// Surveille la distance jusqu’au point d’arrêt : quand il ne reste que le
			// freinage -> DECEL
			double sToEnd = (t.dir > 0) ? (geom.length - t.s) : t.s;
			double minBrakeAtV = (t.v * t.v) / (2 * t.d);
			if (sToEnd <= minBrakeAtV + 1.5 * t.vMax * DT) {
				t.phase = Phase.DECEL;
			}

			// avancer
			t.s += t.dir * t.v * DT;
			if (t.s <= 0) {
				t.s = 0;
				t.phase = Phase.DECEL;
			}
			if (t.s >= geom.length) {
				t.s = geom.length;
				t.phase = Phase.DECEL;
			}
		}
		case DECEL -> {
			// freiner jusqu’à 0 à l’extrémité
			double v0 = t.v;
			t.v = t.v - t.d * DT;
			if (t.v < 0) {
				t.v = 0;
			}

			t.s += t.dir * (v0 * DT - 0.5 * t.d * DT * DT);
			boolean reachedEnd = (t.dir > 0) ? (t.s >= geom.length - 0.001) : (t.s <= 0.001);
			if (reachedEnd || t.v <= 1e-3) {
				// snap
				if (t.dir > 0) {
					t.s = geom.length;
				} else {
					t.s = 0;
				}
				t.v = 0;
				t.decelStart = 0;
				t.phase = Phase.DWELL;
				t.dwellUntil = now + DWELL_MS;
				// on inversera la direction au départ de DWELL
			}
		}
		}
		return t;
	}

	/* ============ API “métier” ============ */

	public Collection<TrainState> list() {
		return trains.values();
	}

	public boolean exists(String id) {
		return trains.containsKey(id);
	}

	/** Créer un train sur une route, avec sa vitesse de pointe (km/h). */
	public void create(String id, String routeId, double lineSpeedKmh, Integer startSeg, Double startProg) {
		var pts = routeService.get(routeId);
		if (pts == null || pts.size() < 2) {
			throw new IllegalArgumentException("routeId inconnu ou trop courte");
		}
		// cache route si besoin
		routes.computeIfAbsent(routeId, k -> buildGeom(pts));
		var geom = routes.get(routeId);

		// position de départ par seg/prog -> s (distance)
		double s0 = 0;
		if (startSeg != null && startProg != null) {
			int seg = Math.max(0, Math.min(startSeg, geom.pts.size() - 2));
			double segLen = hav(geom.pts.get(seg)[0], geom.pts.get(seg)[1], geom.pts.get(seg + 1)[0],
					geom.pts.get(seg + 1)[1]);
			s0 = geom.cum[seg] + Math.max(0, Math.min(1, startProg)) * segLen;
		}

		var st = new TrainState(id, routeId, s0, +1, Math.max(1, lineSpeedKmh) / 3.6, DEFAULT_A, DEFAULT_D);
		st.phase = Phase.DWELL;
		st.dwellUntil = System.currentTimeMillis() + DWELL_MS;
		trains.put(id, st);
		ws.convertAndSend("/topic/trains", TrainWsEvent.add(toDTO(st)));
	}

	/** Modifier la vitesse de pointe (km/h) */
	public void setSpeed(String id, double lineSpeedKmh) {
		var s = trains.get(id);
		if (s != null) {
			s.vMax = Math.max(1, lineSpeedKmh) / 3.6;
		}
	}

	/** Optionnel : régler les accélérations */
	public void setAccelDecel(String id, double a/* m/s² */, double d/* m/s² */) {
		var s = trains.get(id);
		if (s != null) {
			s.a = Math.max(0.1, a);
			s.d = Math.max(0.1, d);
		}
	}

	public void updateTrain(String id, String routeId, double lineSpeedKmh, Integer startSeg, Double startProg) {

		List<double[]> pts = routeService.get(routeId);

		routes.computeIfAbsent(routeId, k -> buildGeom(pts));
		var geom = routes.get(routeId);

		// position de départ par seg/prog -> s (distance)
		double s0 = 0;
		if (startSeg != null && startProg != null) {
			int seg = Math.max(0, Math.min(startSeg, geom.pts.size() - 2));
			double segLen = hav(geom.pts.get(seg)[0], geom.pts.get(seg)[1], geom.pts.get(seg + 1)[0],
					geom.pts.get(seg + 1)[1]);
			s0 = geom.cum[seg] + Math.max(0, Math.min(1, startProg)) * segLen;
		}

		var st = new TrainState(id, routeId, s0, +1, Math.max(1, lineSpeedKmh) / 3.6, DEFAULT_A, DEFAULT_D);
		st.phase = Phase.DWELL;
		st.dwellUntil = System.currentTimeMillis() + DWELL_MS;

		TrainDTO t = toDTO(st);
		if (!trains.containsKey(t.id())) {
			throw new IllegalArgumentException("train inconnue");
		}
		trains.put(t.id(), st);
		ws.convertAndSend("/topic/trains", TrainWsEvent.update(t));
	}

	public void deleteTrain(String id) {
		if (trains.remove(id) != null) {
			ws.convertAndSend("/topic/trains", TrainWsEvent.delete(id));
		}
	}

	public TrainDTO toDTO(TrainState st) {
		var geom = routes.get(st.routeId);
		double[] P = pointAt(geom, st.s);
		Sig sig = switch (st.phase) {
		case DWELL -> Sig.RED;
		case ACCEL, DECEL -> Sig.YELLOW;
		case CRUISE -> Sig.GREEN;
		};
		return new TrainDTO(st.id, P[0], P[1], st.v * 3.6, sig.name(), st.routeId);
	}

	public static TrainDTO toDto(TrainState st) {
		return new TrainDTO(st.id, 0, 0, st.v * 3.6, switch (st.phase) {
		case DWELL -> "RED";
		case ACCEL, DECEL -> "YELLOW";
		case CRUISE -> "GREEN";
		}, st.routeId);
	}

	public long countTrainsOnRoute(String routeId) {
		return trains.values().stream().filter(t -> t.routeId.equals(routeId)).count();
	}

	public boolean hasTrainsOnRoute(String routeId) {
		return countTrainsOnRoute(routeId) > 0;
	}

	public TrainDTO findDto(String id) {
		var st = trains.get(id);
		return (st == null) ? null : toDTO(st);
	}

}
