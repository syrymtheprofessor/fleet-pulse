package com.pingine.fleetpulse.service.trip;

import com.pingine.fleetpulse.domain.Trip;
import com.pingine.fleetpulse.persistence.mongo.TelemetryPoint;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Splits a stream of telemetry points into completed trips.
 * A trip starts on ignition=true and ends on the next ignition=false.
 */
@Component
public class TripDetector {

    public List<Trip> detect(List<TelemetryPoint> points) {

        var cleanedByDuplicateTimestamps = points.stream()
                .collect(Collectors.toMap(TelemetryPoint::getTs, a -> a, (a, b) -> a))
                .values()
                .stream()
                .sorted(Comparator.comparing(TelemetryPoint::getTs))
                .collect(Collectors.toList());

        List<Trip> result = new ArrayList<>();
        List<TelemetryPoint> current = null;
        for (var p : cleanedByDuplicateTimestamps) {
            if (p.isIgnition() && current == null) { // Зажигание включилось — начинаем новую поездку
                current = new ArrayList<>();
                current.add(p);
            } else if (current != null) { // Поездка идёт — добавляем точку в любом случае
                current.add(p);
                if (!p.isIgnition()) { // Зажигание выключилось — поездка завершена
                    result.add(buildTrip(current));
                    current = null;
                }
            }
        }

        return result;
    }

    private Trip buildTrip(List<TelemetryPoint> points) {
        String vehicleId = points.get(0).getVehicleId();

        // 1) Подсчёт distanceKm
        double distanceKm = 0.0;
        for (int i = 1; i < points.size(); i++) {
            distanceKm += haversine(
                    points.get(i - 1).getLat(), points.get(i - 1).getLon(),
                    points.get(i).getLat(),     points.get(i).getLon()
            );
        }

        // 2) Подсчёт avgSpeedKph
        double avgSpeedKph = points.stream()
                .mapToDouble(TelemetryPoint::getSpeed)
                .average()
                .orElse(0.0);

        // 3) Собрать points
        List<Trip.TripPoint> tripPoints = points.stream()
                .map(p -> Trip.TripPoint.builder()
                        .ts(p.getTs().toInstant(ZoneOffset.UTC))
                        .lat(p.getLat())
                        .lon(p.getLon())
                        .speedKph(p.getSpeed())
                        .build())
                .collect(Collectors.toList());

        return Trip.builder()
                .vehicleId(vehicleId)
                .points(tripPoints)
                .distanceKm(distanceKm)
                .avgSpeedKph(avgSpeedKph)
                .startedAt(points.get(0).getTs().toInstant(ZoneOffset.UTC))
                .endedAt(points.get(points.size() - 1).getTs().toInstant(ZoneOffset.UTC))
                .build();
    }

    // Формула Haversine: расстояние между двумя точками на поверхности Земли
    private double haversine(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371.0; // радиус Земли в км

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
