package com.railviz.model;

import java.util.List;

public record RouteDTO(String id, List<double[]> points) {
}
