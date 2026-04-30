package com.pingine.fleetpulse.service.trip;

import com.pingine.fleetpulse.domain.Trip;
import com.pingine.fleetpulse.persistence.mongo.TelemetryPoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Splits a stream of telemetry points into completed trips.
 * A trip starts on ignition=true and ends on the next ignition=false.
 */
@Component
@Slf4j
public class TripDetector {

    public List<Trip> detect(List<TelemetryPoint> points) {
        List<Trip.TripPoint> allPoints = new ArrayList<>(); // своя для каждого вызова
        List<TelemetryPoint> filtered = new ArrayList<>();
        Set<LocalDateTime> seen = new HashSet<>();

        // Без этого никак. Внутри IntStream изоляция должна быть
        for (TelemetryPoint p : points) {
            if (!seen.add(p.getTs())) continue;
            if (p.isIgnition()) {
                filtered.add(p);
            } else if (!filtered.isEmpty()) {
                filtered.add(p);
                break;
            }
        }

        return IntStream.range(1, filtered.size())
                .mapToObj(i -> buildTrip(filtered.get(i - 1), filtered.get(i), allPoints))
                .collect(Collectors.toList());
    }

    private Trip buildTrip(TelemetryPoint from, TelemetryPoint to, List<Trip.TripPoint> allPoints) {
        if (allPoints.isEmpty()) allPoints.add(toTripPoint(from));
        allPoints.add(toTripPoint(to));

        double distanceKm = haversine(from.getLat(), from.getLon(), to.getLat(), to.getLon());
        log.info("Distance from {} to {} is: {}", from.getLat(), from.getLon(), distanceKm);
        double hours = Duration.between(from.getTs(), to.getTs()).toSeconds() / 3600.0;
        double avgSpeed = hours == 0 ? 0 : distanceKm / hours;
        log.info("Average speed is: {}", avgSpeed);

        return Trip.builder()
                .vehicleId(from.getVehicleId())
                .startedAt(from.getTs().toInstant(ZoneOffset.UTC))
                .endedAt(to.getTs().toInstant(ZoneOffset.UTC))
                .distanceKm(distanceKm)
                .avgSpeedKph(avgSpeed)
                .points(new ArrayList<>(allPoints))
                .build();
    }

    private Trip.TripPoint toTripPoint(TelemetryPoint p) {
        return Trip.TripPoint.builder()
                .ts(p.getTs().toInstant(ZoneOffset.UTC))
                .lat(p.getLat())
                .lon(p.getLon())
                .speedKph(p.getSpeed())
                .build();
    }

    private double haversine(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
